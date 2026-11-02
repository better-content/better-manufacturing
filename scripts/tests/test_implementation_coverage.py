"""Validator fixtures contain synthetic bytes, not gameplay qualification evidence."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import struct
import zlib
import random
import test_build_tools as tools

implementation = tools.load('implementation_coverage')

class ImplementationCoverageTests(unittest.TestCase):
    def binding(self, root, name, content):
        path = root / name; path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes): path.write_bytes(content)
        else: path.write_text(content)
        return {'path':name, 'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}

    def fixture(self, root):
        owner = root / 'mod_source/example'; owner.mkdir(parents=True)
        self.binding(root, 'mod_source/example/src/main/Test.java', 'fixture source')
        sources = {'example':{'path':'mod_source/example', 'fingerprint':tools.identity.fingerprint(owner)}}
        source = self.binding(root, 'policy.json', '{}')
        bytecode = self.binding(root, 'mod_source/example/build/classes/Test.class', 'synthetic bytecode')
        dependency = self.binding(root, 'dependency.jar', 'synthetic dependency')
        cases = sorted(implementation.NATIVE - {'teaching'})
        log = '\n'.join('MANUFACTURING_CASE_PASS ' + json.dumps({'subject':'test:drill','case':case}) for case in cases)
        log += '\n1 GAME TESTS COMPLETE\nAll 1 required tests passed\nGame test server shutting down'
        logs = self.binding(root, 'run/log.txt', log)
        execution = {'status':'passed', 'launch_mode':'forge_userdev', 'fixture_kind':'gametest', 'full_pack_accepted':False,
            'production_sources':sources, 'compiled_inputs':[bytecode], 'runtime_dependencies':[dependency], 'logs':[logs],
            'subject_cases':{'test:drill':cases}}
        # A deterministic synthetic PNG tests the validator, not game rendering.
        def chunk(kind, data): return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        rng = random.Random(1)
        pixels = b''.join(b'\0' + rng.randbytes(320 * 4) for _ in range(180))
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 320, 180, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
        image = self.binding(root, 'run/image.png', png)
        review = self.binding(root, 'run/review.json', json.dumps({'subject':'test:drill', 'status':'passed', 'images':[image]}))
        receipt = {'schema':'better_manufacturing.owner_local.v1', 'status':'passed', 'full_pack_accepted':False,
            'subject':'test:drill', 'upstream_sha256':'a'*64, 'production_sources':sources, 'cases':cases,
            'execution':self.binding(root, 'run/execution.json', json.dumps(execution)), 'visual_review':review}
        record = {'implementation':'complete', 'owner_local':'passed', 'pack_acceptance':'pending',
            'source_audit':{key:[source] for key in implementation.AUDITS}, 'implementation_files':[source], 'teaching_files':[source],
            'contract':{'roles':{'head':['tconstruct:pick_head']}, 'quantities':{'head':180}, 'properties':['rate'],
                'reference':'tconstruct:iron', 'accounting_hook':'fixture:stress',
                'lifecycle':{key:{'applicable':False, 'reason':'not used by this validator fixture'} for key in implementation.LIFECYCLES}},
            'evidence':[self.binding(root, 'run/receipt.json', json.dumps(receipt))]}
        client = copy.deepcopy(execution); client['fixture_kind'] = 'client'; client['subject_cases'] = {'test:drill':['teaching']}
        client['logs'] = [self.binding(root, 'run/client.log', 'MANUFACTURING_CASE_PASS {"subject":"test:drill","case":"teaching"}\nMANUFACTURING_CLIENT_COMPLETE\nStopping!')]
        client_receipt = copy.deepcopy(receipt); client_receipt['cases'] = ['teaching']
        client_receipt['execution'] = self.binding(root, 'run/client-execution.json', json.dumps(client))
        record['evidence'].append(self.binding(root, 'run/client-receipt.json', json.dumps(client_receipt)))
        ledger = {'schema':implementation.SCHEMA, 'full_pack_accepted':False, 'entries':{'test:drill':record}}
        return tools.CoverageTests().fixture(), ledger, record, receipt, execution

    def test_machine_evidence_cannot_finish_missing_chemistry_and_teaching(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); selection, ledger, record, _, _ = self.fixture(root)
            implementation.verify_receipts('test:drill', record, implementation.NATIVE, root, 'a'*64)
            errors = implementation.validate(selection, ledger, root)
            self.assertTrue(errors)
            self.assertTrue(all(error.startswith('feature incomplete:') for error in errors), errors)
            self.assertIn('feature incomplete: pla', errors)
            self.assertTrue(__import__('coverage').validate(selection, complete=True))

    def test_completion_flags_and_exclusions_do_not_waive_work(self):
        selection = tools.CoverageTests().fixture()
        ledger = {'schema':implementation.SCHEMA, 'full_pack_accepted':True, 'entries':{}}
        with tempfile.TemporaryDirectory() as directory:
            errors = implementation.validate(selection, ledger, Path(directory))
            self.assertIn('source ledger cannot claim full-pack acceptance', errors)
            self.assertIn('source/local incomplete: test:drill', errors)
            selection['entries'][0].update(disposition='excluded', exclusion_reason='quarantined_content')
            selection['excluded_families'] = ['quarantined_content']
            errors = implementation.validate(selection, ledger, Path(directory))
            self.assertIn('test:drill: missing exclusion audit', errors)

    def test_artifact_or_source_drift_invalidates_receipt(self):
        for path in ['dependency.jar', 'mod_source/example/src/main/Test.java', 'mod_source/example/build/classes/Test.class', 'run/image.png']:
            with self.subTest(path=path), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); _, _, record, _, _ = self.fixture(root)
                (root / path).write_text('changed')
                with self.assertRaises(ValueError): implementation.verify_receipts('test:drill', record, implementation.NATIVE, root, 'a'*64)

    def test_unexecuted_cases_wrong_subject_and_failed_run_are_rejected(self):
        for change in ['subject', 'cases', 'shutdown', 'source_scope']:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); _, _, record, receipt, execution = self.fixture(root)
                if change == 'subject': receipt['subject'] = 'test:other'
                elif change == 'cases': execution['subject_cases']['test:drill'].append('not_executed')
                elif change == 'source_scope': execution['full_pack_accepted'] = True
                else:
                    log = (root / 'run/log.txt').read_text().replace('Game test server shutting down', 'aborted')
                    execution['logs'] = [self.binding(root, 'run/log.txt', log)]
                receipt['execution'] = self.binding(root, 'run/execution.json', json.dumps(execution))
                record['evidence'] = [self.binding(root, 'run/receipt.json', json.dumps(receipt))]
                with self.assertRaises(ValueError): implementation.verify_receipts('test:drill', record, implementation.NATIVE, root, 'a'*64)

    def test_teaching_cannot_use_a_server_only_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); _, _, record, receipt, execution = self.fixture(root)
            execution['subject_cases']['test:drill'].append('teaching')
            log = (root / 'run/log.txt').read_text() + '\nMANUFACTURING_CASE_PASS {"subject":"test:drill","case":"teaching"}'
            execution['logs'] = [self.binding(root, 'run/log.txt', log)]
            receipt['cases'].append('teaching')
            receipt['execution'] = self.binding(root, 'run/execution.json', json.dumps(execution))
            record['evidence'] = [self.binding(root, 'run/receipt.json', json.dumps(receipt))]
            with self.assertRaisesRegex(ValueError, 'real-client'): implementation.verify_receipts('test:drill', record, implementation.NATIVE, root, 'a'*64)

    def test_pin_changes_cannot_reuse_old_source_audits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / 'mods').mkdir(); cache = root / 'cache'; cache.mkdir()
            archive = cache / 'example.jar'; archive.write_bytes(b'original pinned bytes')
            source = {'sources':{'example':{'artifact':archive.name, 'sha256':implementation.digest(archive)}}}
            pin = root / 'mods/example.pw.toml'
            pin.write_text('filename="example.jar"\n[download]\nhash-format="sha1"\nhash="' + hashlib.sha1(archive.read_bytes()).hexdigest() + '"\n')
            self.assertEqual([], implementation.validate_pinned_sources(source, root, cache))
            archive.write_bytes(b'new bytes')
            self.assertTrue(implementation.validate_pinned_sources(source, root, cache))
            pin.unlink()
            self.assertTrue(implementation.validate_pinned_sources(source, root, cache))

    def test_path_escape_and_missing_lifecycle_case_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); selection, ledger, record, _, _ = self.fixture(root)
            with self.assertRaises(ValueError): implementation.bound_file({'path':'../outside','sha256':'a'*64}, root)
            record['contract']['lifecycle']['contraption'] = {'applicable':True, 'reason':'physical moving machine'}
            self.assertIn('test:drill: missing cases: contraption', implementation.validate(selection, ledger, root))

if __name__ == '__main__': unittest.main()
