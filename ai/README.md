## Environment

- Python: 3.12
- CUDA: 13.2


```
# confirm the CUDA version of local PC
nvcc --version
# or
nvidia-smi

# get the pytorch installation url from website
# latest: https://pytorch.org/get-started/locally/
# previous: https://pytorch.org/get-started/previous-versions/
# example (for reference):
# CUDA 13.0: pip3 install torch torchvision --index-url https://download.pytorch.org/whl/cu130
# CUDA 11.8: pip install torch==2.7.1 torchvision==0.22.1 torchaudio==2.7.1 --index-url https://download.pytorch.org/whl/cu118

# install uv (https://docs.astral.sh/uv/)
pip install uv

# install all runtime dependencies (torch/torchvision resolved via pytorch-cu130 index in pyproject.toml)
uv sync --no-dev

# install ffmpeg from https://ffmpeg.org/download.html
# add ffmpeg to PATH

# fix local import root
$env:PYTHONPATH="src"

# start inference service
python scripts/serve.py
```

