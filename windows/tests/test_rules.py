import unittest

from floatap_win.rules import DraftIssue, JobDraft, StepDraft, check_draft


class RulesTest(unittest.TestCase):
    def test_interval_raised(self):
        checked = check_draft(
            JobDraft(
                name="a",
                duration_sec=60,
                click_limit=None,
                countdown_sec=3,
                pause_on_user_touch=True,
                existing_count=0,
                is_new=True,
                steps=[
                    StepDraft("点击", 10, 10, 100, 100, 0, 10, None),
                ],
            )
        )
        self.assertIsNone(checked.issue)
        self.assertTrue(checked.interval_adjusted)
        self.assertEqual(checked.steps[0].interval_ms, 50)

    def test_missing_point(self):
        checked = check_draft(
            JobDraft(
                name="a",
                duration_sec=60,
                click_limit=None,
                countdown_sec=3,
                pause_on_user_touch=True,
                existing_count=0,
                is_new=True,
                steps=[StepDraft("点击", -1, -1, 0, 0, 0, 500, None)],
            )
        )
        self.assertEqual(checked.issue, DraftIssue.NO_POINT)

    def test_loop_only_ok(self):
        checked = check_draft(
            JobDraft(
                name="a",
                duration_sec=None,
                click_limit=None,
                countdown_sec=3,
                pause_on_user_touch=True,
                existing_count=0,
                is_new=True,
                loop_limit=2,
                steps=[
                    StepDraft("a", 1, 1, 10, 10, 0, 100, None),
                    StepDraft("b", 2, 2, 10, 10, 0, 200, None),
                ],
            )
        )
        self.assertIsNone(checked.issue)
        self.assertEqual(checked.loop_limit, 2)


if __name__ == "__main__":
    unittest.main()
