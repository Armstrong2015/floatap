import random
import unittest

from floatap_win.models import StopReason
from floatap_win.schedule import ScheduleInput, schedule, wait_millis


class ScheduleTest(unittest.TestCase):
    def test_ten_second_run_at_500ms(self):
        result = schedule(
            ScheduleInput(waits_ms=[500], countdown_sec=3, duration_ms=10_000)
        )
        self.assertEqual(result.stop, StopReason.DURATION)
        self.assertEqual(len(result.clicks), 20)
        self.assertEqual(result.clicks[0].at_ms, 3_500)
        self.assertEqual(result.clicks[-1].at_ms, 13_000)
        gaps = [b.at_ms - a.at_ms for a, b in zip(result.clicks, result.clicks[1:])]
        self.assertTrue(all(gap == 500 for gap in gaps))

    def test_click_limit(self):
        result = schedule(ScheduleInput(waits_ms=[500], countdown_sec=0, click_limit=10))
        self.assertEqual(result.stop, StopReason.CLICK_LIMIT)
        self.assertEqual(len(result.clicks), 10)

    def test_three_steps_loop_twice(self):
        result = schedule(
            ScheduleInput(waits_ms=[100, 200, 300], countdown_sec=0, loop_limit=2)
        )
        self.assertEqual(result.stop, StopReason.LOOP_LIMIT)
        self.assertEqual([c.step_index for c in result.clicks], [0, 1, 2, 0, 1, 2])

    def test_random_waits(self):
        rng = random.Random(20261018)
        samples = [wait_millis(400, 600, rng) for _ in range(1000)]
        self.assertTrue(all(400 <= value <= 600 for value in samples))
        self.assertGreater(len(set(samples)), 1)
        self.assertEqual(wait_millis(500, 500, random.Random(1)), 500)
        self.assertEqual(wait_millis(500, 100, random.Random(1)), 500)


if __name__ == "__main__":
    unittest.main()
