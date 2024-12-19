import trace
import uuid
import opik
import time
import random
import string
import logging

from opik.plugins.pytest.decorator import opik_context

logging.basicConfig(
    level=logging.WARNING,
    format='%(levelname)s: %(message)s'
)

LOGGER = logging.getLogger(__name__)


def create_random_string(length: int) -> str:
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

@opik.track
def create_span(input: str) -> str:
    output = create_random_string(100)
    return output

@opik.track(project_name="performance_test")
def create_trace(input: str) -> str:

    for i in range(3):
        input = create_random_string(100)
        create_span(input)

    return create_random_string(100), opik_context.get_current_trace_data().id

def log_traces_and_spans(nb_traces: int) -> str:
    for i in range(nb_traces):
        uuid_str = str(uuid.uuid4())
        _, trace_id = create_trace(create_random_string(100), )
    
    opik.flush_tracker()
    return uuid_str

def check_output(trace_id: str):
    opik_client = opik.Opik()
    start_time = time.time()
    while True:
        try:
            opik_client.get_trace_content(trace_id)
            break
        except:
            time.sleep(0.5)
        
        if time.time() - start_time > 60:
            raise Exception("Timed out waiting for the trace to be available in the UI - Took longer than 60 seconds")

if __name__ == "__main__":
    import click

@click.command()
@click.option('--num-traces', default=1000, help='Number of traces to generate')
def main(num_traces):
    start_time = time.time()
    last_trace_unique_id = log_traces_and_spans(num_traces)
    end_time_logging = time.time()
    
    start_time_available_in_ui_check = time.time()
    check_output(last_trace_unique_id)
    end_time_available_in_ui_check = time.time()

    print("\n---------------- Performance results ----------------")
    print(f"Time to log traces and spans           : {end_time_logging - start_time:.2f} seconds")
    print(f"Time before traces are available in UI : {end_time_available_in_ui_check - start_time_available_in_ui_check:.2f} seconds")
    print(f'Total time                             : {time.time() - start_time:.2f} seconds')

if __name__ == "__main__":
    main()
