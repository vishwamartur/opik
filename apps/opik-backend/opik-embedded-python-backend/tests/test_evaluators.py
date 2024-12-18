def test_other_method_returns_method_not_allowed(client):
    response = client.get("/v1/private/evaluators")
    assert response.status_code == 405


def test_options_method_returns_ok(client):
    response = client.options("/v1/private/evaluators")
    assert response.status_code == 200
    assert response.get_json() is None


def test_json_data(client):
    response = client.post("/v1/private/evaluators", json={
        "data": {
            "output": "abc",
            "reference": "abc"
        }
    })
    assert response.status_code == 400
    assert response.json["error"] == "400 Bad Request: Field 'code' is missing in the request"
