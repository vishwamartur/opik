---
sidebar_position: 100
sidebar_label: Custom Metric
---

# Custom Metric

Opik allows you to define your own metrics. This is useful if you have a specific metric that is not already implemented.

## Defining a custom metric

To define a custom metric, you need to subclass the `Metric` class and implement the `score` method and an optional `ascore` method:

```python
from opik.evaluation.metrics import base_metric, score_result

class MyCustomMetric(base_metric.BaseMetric):
    def __init__(self, name: str):
        self.name = name

    def score(self, input: str, output: str, **ignored_kwargs: Any):
        # Add you logic here

        return score_result.ScoreResult(
            value=0,
            name=self.name,
            reason="Optional reason for the score"
        )
```

The `score` method should return a `ScoreResult` object. The `ascore` method is optional and can be used to compute the asynchronously if needed.

:::tip
You can also return a list of `ScoreResult` objects as part of your custom metric. This is useful if you want to return multiple scores for a given input and output pair.
:::

This metric can now be used in the `evaluate` function as explained here: [Evaluating LLMs](/evaluation/evaluate_your_llm).

## Writing an LLM as a Judge metric

If you want to write an LLM as a judge metric, you can either use the [G-eval metric](/evaluation/metrics/g-eval) or implement your own custom metric.

Opik LLM as a Judge metrics use the [LiteLLM](https://docs.litellm.ai/docs/) library to support a wide range of LLMs. We can follow a similar pattern to create our own custom metric:

```python
from opik.evaluation.metrics import base_metric, score_result
from opik.evaluation.models import litellm_chat_model
from pydantic import BaseModel
import json

class LLMJudgeResult(BaseModel):
    score: int
    reason: str

class LLMJudgeMetric(base_metric.BaseMetric):
    def __init__(self, name: str = "Factuality check", model_name: str = "gpt-4o"):
        self.name = name
        self.llm_client = litellm_chat_model.LiteLLMChatModel(model_name=model_name)
        self.prompt_template = """
        You are an impartial judge evaluating the following claim for factual accuracy. Analyze it carefully and respond with a number between 0 and 1: 1 if completely accurate, 0.5 if mixed accuracy, or 0 if inaccurate. Then provide one brief sentence explaining your ruling.

        Claim to evaluate: {output}
        """

    def score(self, output: str, **ignored_kwargs: Any):
        """
        Score the output of an LLM.

        Args:
            output: The output of an LLM to score.
            **ignored_kwargs: Any additional keyword arguments. This is important so that the metric can be used in the `evaluate` function.
        """
        # Construct the prompt based on the output of the LLM
        prompt = self.prompt_template.format(output=output)

        # Generate and parse the response from the LLM
        response = self.llm_client.generate_string(input=prompt, response_format=LLMJudgeResult)
        response_dict = json.loads(response)

        return score_result.ScoreResult(
            name=self.name,
            value=response_dict["score"],
            reason=response_dict["reason"]
        )
```

You can then use this metric to score your LLM outputs:

```python
metric = LLMJudgeMetric()

metric.score(output="Paris is the capital of France")
```

:::tip
You can replace the `litellm_chat_model.LiteLLMChatModel` with any other LLM provider. For example, you could use the OpenAI client to score your LLM outputs.
:::
