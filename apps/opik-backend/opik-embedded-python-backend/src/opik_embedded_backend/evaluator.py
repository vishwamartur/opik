import inspect
from types import ModuleType
from typing import Type, Union, List, Any, Dict

from flask import request, abort, jsonify, Blueprint, current_app
from opik.evaluation.metrics import BaseMetric
from opik.evaluation.metrics.score_result import ScoreResult
from werkzeug.exceptions import HTTPException

from .helpers import id_helpers

evaluator = Blueprint('evaluator', __name__)


def get_module(code: str, module_name: str = id_helpers.uuid4_str()) -> ModuleType:
    module: ModuleType = ModuleType(module_name)
    exec(code, module.__dict__)
    return module


def get_metric_class(module: ModuleType) -> Type[BaseMetric]:
    for _, cls in inspect.getmembers(module, inspect.isclass):
        if issubclass(cls, BaseMetric):
            return cls


def evaluate_metric(metric_class: Type[BaseMetric], data: Dict[Any, Any]) -> Union[ScoreResult, List[ScoreResult]]:
    base_metric: BaseMetric = metric_class()
    result: Union[ScoreResult, List[ScoreResult]] = base_metric.score(**data)
    scores: List[ScoreResult] = []
    if isinstance(result, ScoreResult):
        scores = [result]
    elif isinstance(result, list):
        for item in result:
            if isinstance(item, ScoreResult):
                scores.append(item)
    return scores


@evaluator.errorhandler(400)
def bad_request(exception: HTTPException):
    return jsonify(error=str(exception)), 400


@evaluator.route("/v1/private/evaluators", methods=["POST"])
def execute_evaluator():
    if request.method != "POST":
        return

    payload: Any = request.get_json(force=True)

    code: str = payload.get("code")
    if code is None:
        abort(400, "Field 'code' is missing in the request")

    data: Dict[Any, Any] = payload.get("data")
    if data is None:
        abort(400, "Field 'data' is missing in the request")

    try:
        module: ModuleType = get_module(code)
        metric_class: Type[BaseMetric] = get_metric_class(module)
    except Exception as exception:
        current_app.logger.info("Exception getting metric class, message '%s', code '%s'", exception, code)
        abort(400, "Field 'code' contains invalid Python code")

    if metric_class is None:
        abort(400,
              "Field 'code' in the request doesn't contain a subclass implementation of 'opik.evaluation.metrics.BaseMetric'")

    scores: List[ScoreResult] = []
    try:
        scores = evaluate_metric(metric_class, data)
    except Exception as exception:
        current_app.logger.info("Exception evaluating metric, message '%s', data '%s', code '%s'", exception, data, code)
        abort(400, "The provided 'code' and 'data' fields can't be evaluated'")

    return jsonify({"scores": scores})
