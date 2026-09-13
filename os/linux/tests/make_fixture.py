from pathlib import Path
import sys
from test_collection import fixture
root=Path(sys.argv[1])
root.mkdir(parents=True,exist_ok=True)
print(fixture(root))
