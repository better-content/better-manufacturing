import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
import sys

SCRIPTS = Path(__file__).resolve().parents[1]
def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + '.py'))
    module = importlib.util.module_from_spec(spec); sys.modules[name] = module; spec.loader.exec_module(module)
    return module
identity = load('source_identity')
coverage = load('coverage')
native = load('native_receipt')

class SourceIdentityTests(unittest.TestCase):
    def test_dirty_production_changes_but_tests_and_build_do_not(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root / 'src/main').mkdir(parents=True)
            code = root / 'src/main/Test.java'; code.write_text('one')
            before = identity.fingerprint(root)
            (root / 'build').mkdir(); (root / 'build/output.jar').write_bytes(b'ignored')
            (root / 'src/test').mkdir(); (root / 'src/test/Test.java').write_text('test')
            self.assertEqual(before, identity.fingerprint(root))
            code.write_text('two')
            self.assertNotEqual(before, identity.fingerprint(root))
            self.assertIn('commit=source-snapshot-sha256:', identity.metadata(root))
    def test_runtime_markdown_is_a_production_input(self):
        self.assertTrue(identity.production('src/main/resources/guide.md'))
        self.assertFalse(identity.production('docs/guide.md'))
        self.assertFalse(identity.production('src/test/java/Test.java'))
        self.assertTrue(identity.production('gradle/wrapper/gradle-wrapper.jar'))

class NativeReceiptTests(unittest.TestCase):
    def test_wrong_population_and_source_drift_fail(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); fixture = root / 'build/gametest/fresh'; fixture.mkdir(parents=True)
            (root / 'build/classes/java/main').mkdir(parents=True)
            (root / 'build/resources/main/META-INF').mkdir(parents=True)
            (fixture / 'logs').mkdir(); log = fixture / 'logs/latest.log'
            log.write_text('9 GAME TESTS COMPLETE\nAll 9 required tests passed\nGame test server shutting down')
            (fixture / 'runtime-classpath.txt').write_text('')
            (fixture / 'fixture-inputs.txt').write_text(str(root / 'build/classes/java/main'))
            (root / 'build/resources/main/META-INF/better-content-source.properties').write_text(identity.metadata(root))
            with self.assertRaises(ValueError): native.receipt(root, fixture, 8)
            result = native.receipt(root, fixture, 9)
            self.assertFalse(result['full_roster_accepted'])
            self.assertEqual('forge_userdev', result['launch_mode'])
            (root / 'changed.java').write_text('production changed')
            with self.assertRaises(ValueError): native.receipt(root, fixture, 9)
    def test_failed_or_missing_shutdown_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root / 'logs').mkdir()
            for text in ['9 GAME TESTS COMPLETE\n1 required tests failed',
                         '9 GAME TESTS COMPLETE\nAll 9 required tests passed']:
                (root / 'logs/latest.log').write_text(text)
                with self.assertRaises(ValueError): native.receipt(root, root, 9)

class CoverageTests(unittest.TestCase):
    def fixture(self):
        return {'schema':'better_manufacturing.coverage.v1', 'completion_requires_full_roster':True,
            'new_component_ceiling':12, 'planned_component_ids':['test:component_' + str(i) for i in range(7)],
            'sources':{'test':{'sha256':'a'*64}}, 'unexpanded_recipe_only_families':[],
            'entries':[{'id':'test:drill', 'roles':{'head':['tconstruct:pick_head']},
                'disposition':'material_dependent', 'adapter':'test:cutting', 'derived_properties':['rate'],
                'status':'pending', 'acquisition_audit':'pending', 'native_reference_audit':'pending',
                'native_recipe_ids':['test:drill'], 'pack_recipe_ids':[], 'verification':[]}]}
    def test_selection_is_not_acceptance(self):
        data = self.fixture()
        self.assertEqual([], coverage.validate(data))
        self.assertTrue(coverage.validate(data, complete=True))
    def test_changing_a_completion_flag_does_not_create_evidence(self):
        data = self.fixture(); entry = data['entries'][0]
        entry.update(status='verified', acquisition_audit='verified', native_reference_audit='verified')
        self.assertTrue(coverage.validate(data, complete=True))
    def test_duplicates_and_budget_rejected(self):
        data = self.fixture(); data['entries'].append(copy.deepcopy(data['entries'][0]))
        self.assertTrue(coverage.validate(data))
        data = self.fixture(); data['new_component_ceiling'] = 13
        self.assertTrue(coverage.validate(data))
    def test_recipe_only_cannot_claim_runtime_effects(self):
        data = self.fixture(); data['entries'][0]['disposition'] = 'recipe_only'
        self.assertTrue(coverage.validate(data))
    def test_unknown_registry_identity_rejected(self):
        data = self.fixture(); data['entries'][0]['id'] = 'not a registry id'
        self.assertTrue(coverage.validate(data))

if __name__ == '__main__': unittest.main()
