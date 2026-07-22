"""Shared test fixtures."""
import os
import tempfile
import pytest


@pytest.fixture
def tmp_db_path():
    """Provide a temporary database file path.

    Creates a temp file, deletes it (so SQLite creates fresh),
    and cleans up after the test.
    """
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        path = f.name
    os.unlink(path)
    yield path
    if os.path.exists(path):
        os.unlink(path)
