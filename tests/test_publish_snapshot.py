import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import publish_snapshot as publisher
from snapshot_schema import load_snapshot, write_snapshot_atomic
from test_snapshot_schema import example


class PublisherGitEndToEndTests(unittest.TestCase):
    def git(self, directory, *args):
        result = subprocess.run(['git', '-C', str(directory), *args], capture_output=True, text=True, check=True)
        return result.stdout.strip()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='agent-usage-git-tests-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.bare = self.root / 'remote.git'
        self.git(self.root, 'init', '--bare', str(self.bare))
        self.seed = self.root / 'seed'
        self.git(self.root, 'init', '--initial-branch=main', str(self.seed))
        self.identity(self.seed)
        (self.seed / 'README.md').write_text('Synthetic test data repository\n')
        self.git(self.seed, 'add', 'README.md')
        self.git(self.seed, 'commit', '-m', 'Initialize test repository')
        self.git(self.seed, 'remote', 'add', 'origin', str(self.bare))
        self.git(self.seed, 'push', '-u', 'origin', 'main')
        self.git(self.bare, 'symbolic-ref', 'HEAD', 'refs/heads/main')
        self.clone = self.root / 'data'
        self.git(self.root, 'clone', str(self.bare), str(self.clone))
        self.identity(self.clone)
        self.input = self.root / 'snapshot.json'
        write_snapshot_atomic(self.input, example())
        self.initial_sha = self.git(self.clone, 'rev-parse', 'HEAD')

    def identity(self, repo):
        self.git(repo, 'config', 'user.name', 'Synthetic Test')
        self.git(repo, 'config', 'user.email', 'test@example.invalid')

    def publish(self, **kwargs):
        return publisher.publish_snapshot(self.clone, self.input, **kwargs)

    def test_real_bare_remote_publish_only_one_json_and_remote_sha_matches(self):
        report = self.publish(file='stats/usage-latest.json')
        self.assertTrue(report['remoteVerified'])
        self.assertTrue(report['changed'])
        self.assertFalse(report['phoneReceiptVerified'])
        self.assertEqual(self.git(self.bare, 'rev-parse', 'refs/heads/main'), report['commit'])
        self.assertEqual(self.git(self.clone, 'diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD'), 'stats/usage-latest.json')
        remote_content = self.git(self.bare, 'show', 'refs/heads/main:stats/usage-latest.json')
        self.assertEqual(json.loads(remote_content), load_snapshot(self.input))
        self.assertEqual(self.git(self.clone, 'status', '--porcelain'), '')

    def test_same_snapshot_does_not_create_another_commit(self):
        first = self.publish()
        second = self.publish()
        self.assertFalse(second['changed'])
        self.assertEqual(first['commit'], second['commit'])

    def test_remote_fast_forward_is_preserved_before_publish(self):
        (self.seed / 'README.md').write_text('Remote user update stays intact\n')
        self.git(self.seed, 'commit', '-am', 'Remote documentation update')
        remote_parent = self.git(self.seed, 'rev-parse', 'HEAD')
        self.git(self.seed, 'push', 'origin', 'main')
        report = self.publish()
        self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD^'), remote_parent)
        self.assertEqual((self.clone / 'README.md').read_text(), 'Remote user update stays intact\n')
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), report['commit'])

    def test_dirty_untracked_staged_and_tracked_work_are_refused(self):
        for kind in ('untracked', 'staged', 'tracked'):
            with self.subTest(kind=kind):
                if kind == 'tracked':
                    target = self.clone / 'README.md'; target.write_text('User local changes\n')
                else:
                    target = self.clone / 'private-local.txt'; target.write_text('Synthetic local work\n')
                    if kind == 'staged': self.git(self.clone, 'add', target.name)
                with self.assertRaises(RuntimeError): self.publish()
                self.assertFalse((self.clone / 'usage-latest.json').exists())
                self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), self.initial_sha)
                self.git(self.clone, 'reset', '--hard', 'HEAD')
                self.git(self.clone, 'clean', '-fd')

    def test_local_unpublished_commit_is_not_pushed(self):
        (self.clone / 'README.md').write_text('User unpublished change\n')
        self.git(self.clone, 'commit', '-am', 'Unpublished work')
        with self.assertRaises(RuntimeError): self.publish()
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), self.initial_sha)
        self.assertFalse((self.clone / 'usage-latest.json').exists())

    def test_invalid_payload_never_replaces_or_pushes_data(self):
        data = example(); data['access_token'] = 'synthetic-not-for-upload'
        self.input.write_text(json.dumps(data))
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), self.initial_sha)
        self.assertFalse((self.clone / 'usage-latest.json').exists())

    def test_path_escapes_and_symlinks_are_refused(self):
        for name in ('../outside.json', '/tmp/outside.json', 'stats/../../outside.json', '.git/private.json', 'stats\\outside.json', 'not-json.txt'):
            with self.subTest(name=name):
                with self.assertRaises(ValueError): self.publish(file=name)
        outside = self.root / 'outside'; outside.mkdir()
        (self.clone / 'linked').symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError): self.publish(file='linked/usage.json')
        self.assertFalse((outside / 'usage.json').exists())

    def test_app_source_and_requested_other_branch_are_refused(self):
        with self.assertRaises(RuntimeError): self.publish(branch='other')
        app = self.clone / 'android/AndroidManifest.xml'; app.parent.mkdir(); app.write_text('<manifest/>')
        self.git(self.clone, 'add', 'android/AndroidManifest.xml')
        self.git(self.clone, 'commit', '-m', 'Synthetic application source marker')
        self.git(self.clone, 'push', 'origin', 'main')
        with self.assertRaises(RuntimeError): self.publish()
        self.assertFalse((self.clone / 'usage-latest.json').exists())


    def test_remote_addresses_refuse_embedded_credentials_without_echoing_them(self):
        for address in ('https://test-only:private@example.invalid/data.git',
                        'https://test-only@example.invalid/data.git',
                        'https://example.invalid/data.git?access_token=test-only',
                        'https://example.invalid/data.git?apiKey=test-only',
                        'ssh://git:test-only@example.invalid/data.git'):
            with self.subTest(address=address):
                self.git(self.clone, 'remote', 'set-url', 'origin', address)
                with self.assertRaises(ValueError) as caught: self.publish()
                self.assertNotIn('test-only', str(caught.exception))
        for address in ('git@example.invalid:owner/data.git', 'ssh://git@example.invalid/owner/data.git', 'https://example.invalid/owner/data.git', str(self.bare), self.bare.as_uri()):
            publisher._remote_url(address)
        self.git(self.clone, 'remote', 'set-url', 'origin', str(self.bare))
        self.git(self.clone, 'remote', 'set-url', '--push', 'origin', str(self.root / 'different.git'))
        with self.assertRaises(ValueError): self.publish()

    def test_same_provider_observation_cannot_roll_back_after_remote_fast_forward(self):
        data = example(); data['observedAt'] += 30
        for key in ('quota', 'daily', 'resetCards'): data[key]['observedAt'] = data['observedAt']
        write_snapshot_atomic(self.seed / 'usage-latest.json', data)
        self.git(self.seed, 'add', 'usage-latest.json')
        self.git(self.seed, 'commit', '-m', 'Newer remote provider data')
        newer_sha = self.git(self.seed, 'rev-parse', 'HEAD')
        self.git(self.seed, 'push', 'origin', 'main')
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(self.git(self.clone, 'rev-parse', 'HEAD'), newer_sha)
        self.assertEqual(load_snapshot(self.clone / 'usage-latest.json'), data)
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), newer_sha)
        # An explicitly different provider is a new data source, not an older
        # version of the same provider's snapshot.
        replacement = example(); replacement['provider'] = 'Other Demo Agent'
        write_snapshot_atomic(self.input, replacement)
        self.assertTrue(self.publish()['remoteVerified'])

    @unittest.skipIf(os.name == 'nt', 'POSIX hook fixture; publisher itself is cross-platform')
    def test_hook_cannot_smuggle_extra_files_into_remote_commit(self):
        hook = self.clone / '.git/hooks/pre-commit'
        hook.write_text('#!/bin/sh\nprintf "test-only\\n" > unexpected.txt\ngit add unexpected.txt\n')
        hook.chmod(0o755)
        with self.assertRaises(RuntimeError): self.publish()
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), self.initial_sha)

    @unittest.skipIf(os.name == 'nt', 'POSIX hook fixture; publisher itself is cross-platform')
    def test_hook_cannot_replace_validated_json_before_remote_push(self):
        hook = self.clone / '.git/hooks/pre-commit'
        hook.write_text('#!/bin/sh\nprintf "{\\\"unexpected\\\":true}\\n" > usage-latest.json\ngit add usage-latest.json\n')
        hook.chmod(0o755)
        with self.assertRaises(RuntimeError): self.publish()
        self.assertEqual(self.git(self.bare, 'rev-parse', 'main'), self.initial_sha)


if __name__ == '__main__': unittest.main()
