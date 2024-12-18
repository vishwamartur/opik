import inspect
import types

from opik.evaluation import metrics

IN_MEMORY_CUSTOM_EQUALS = """
from typing import Any

from opik.evaluation.metrics import base_metric, score_result


class InMemoryCustomEquals(base_metric.BaseMetric):
    def __init__(
        self,
        name: str = "my_custom_equals_metric",
    ):
        super().__init__(
            name=name,
            track=False,
        )

    def score(
        self, output: str, reference: str, **ignored_kwargs: Any
    ) -> score_result.ScoreResult:
        value = 1.0 if output == reference else 0.0
        return score_result.ScoreResult(value=value, name=self.name)
"""

IN_MEMORY_CUSTOM_EQUALS_2 = """
from typing import Any

from opik.evaluation.metrics import base_metric, score_result


class InMemoryCustomEquals(base_metric.BaseMetric):
    def __init__(
        self,
        name: str = "my_custom_equals_metric",
    ):
        super().__init__(
            name=name,
            track=False,
        )

    def score(
        self, output: str, reference: str, **ignored_kwargs: Any
    ) -> score_result.ScoreResult:
        value = 1.0 if output == reference else 0.0
        return [score_result.ScoreResult(value=value, name=self.name), score_result.ScoreResult(value=0.5, name=self.name)]
"""

if __name__ == '__main__':
    print("Hello World!")
    module_name = "in_memory_custom_equals"
    content = IN_MEMORY_CUSTOM_EQUALS
    payload_ok = {"output": "abc", "reference": "abc"}
    payload_not_ok = {"output": "abc", "reference": "a"}

    module = types.ModuleType(module_name)
    exec(content, module.__dict__)
    for name, member in inspect.getmembers(module, inspect.isclass):
        if issubclass(member, metrics.BaseMetric):
            print(member)
            metric = member()
            result_ok = metric.score(**payload_ok)
            print(result_ok)
            result_not_ok = metric.score(**payload_not_ok)
            print(result_not_ok)
