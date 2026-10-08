import os
from pathlib import Path
import unittest

from pull_repository import pull_repository
import test_publish_snapshot as publisher_tests


class PullRepositoryTests(unittest.TestCase):
    # Reuse the isolated bare-Git fixture without rediscovering publisher tests.
    git = publisher_tests.PublisherGitEndToEndTests.git
    setUp = publisher_tests.PublisherGitEndToEndTests.setUp
    identity = publisher_tests.PublisherGitEndToEndTests.identity
    def test_real_fast_forward_retains_code_and_verifies_remote(self):
        (self.seed / 'README.md').write_text('New code documentation\n')
        script = self.seed / 'never-run.sh'
        script.write_text('#!/bin/sh\ntouch executed-unexpectedly\n')
        self.git(self.seed, 'add', 'README.md', script.name)
        self.git(self.seed, 'commit', '-m', 'Remote code update')
        self.git(self.seed, 'push', 'origin', 'main')
        expected = self.git(self.seed, 'rev-parse', 'HEAD')
        report = pull_repository(self.clone)
        self.assertEqual(report['commit'], expected)
        self.assertTrue(report['updated'])
        self.assertTrue(report['remoteVerified'])
        self.assertFalse(report['codeExecuted'])
        self.assertEqual((self.clone / 'README.md').read_text(), 'New code documentation\n')
        self.assertFalse((self.clone / 'executed-unexpectedly').exists())
        self.assertFalse(pull_repository(self.clone)['updated'])

    def test_dirty_checkout_is_preserved(self):
        target = self.clone / 'README.md'; target.write_text('Uncommitted user code\n')
        with self.assertRaises(RuntimeError): pull_repository(self.clone)
        self.assertEqual(target.read_text(), 'Uncommitted user code\n')
        self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD'), self.initial_sha)

    def test_diverged_and_unpublished_commits_are_preserved(self):
        (self.clone / 'README.md').write_text('Local user commit\n')
        self.git(self.clone, 'commit', '-am', 'Local user work')
        local_sha = self.git(self.clone, 'rev-parse', 'HEAD')
        with self.assertRaises(RuntimeError): pull_repository(self.clone)
        (self.seed / 'README.md').write_text('Remote independent commit\n')
        self.git(self.seed, 'commit', '-am', 'Remote independent work')
        self.git(self.seed, 'push', 'origin', 'main')
        with self.assertRaises(RuntimeError): pull_repository(self.clone)
        self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD'), local_sha)
        self.assertEqual((self.clone / 'README.md').read_text(), 'Local user commit\n')

    def test_credential_bearing_remote_is_rejected_without_echoing_secret(self):
        for address in ('https://test-only:private@example.invalid/code.git', 'https://example.invalid/code.git?access_token=test-only'):
            self.git(self.clone, 'remote', 'set-url', 'origin', address)
            with self.assertRaises(ValueError) as caught: pull_repository(self.clone)
            self.assertNotIn('test-only', str(caught.exception))
            self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD'), self.initial_sha)


    def test_external_content_filters_are_refused_before_they_can_execute(self):
        self.git(self.clone, 'config', 'filter.synthetic.smudge', 'sh never-run.sh')
        with self.assertRaises(RuntimeError): pull_repository(self.clone)
        self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD'), self.initial_sha)

    @unittest.skipIf(os.name == 'nt', 'POSIX hook fixture; helper itself is cross-platform')
    def test_post_merge_and_reference_hooks_are_not_executed(self):
        flag = self.root / 'hook-was-executed'
        for name in ('post-merge', 'reference-transaction'):
            hook = self.clone / '.git/hooks' / name
            hook.write_text('#!/bin/sh\ntouch "' + str(flag) + '"\n')
            hook.chmod(0o755)
        (self.seed / 'README.md').write_text('Hook-free update\n')
        self.git(self.seed, 'commit', '-am', 'Remote update')
        self.git(self.seed, 'push', 'origin', 'main')
        self.assertTrue(pull_repository(self.clone)['updated'])
        self.assertFalse(flag.exists())


if __name__ == '__main__': unittest.main()
