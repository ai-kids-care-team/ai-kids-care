## Environment

- Python: 3.14
- CUDA: 13.2


```
# install transformer library
pip install transformers

# confirm the CUDA version of local PC
nvcc --version
# or
nvidia-smi

# get the pytorch installation url from website
# latest: https://pytorch.org/get-started/locally/
# previous: https://pytorch.org/get-started/previous-versions/
# example: for cuda 11.8 version

# CUDA 13.0
pip3 install torch torchvision --index-url https://download.pytorch.org/whl/cu130
# CUDA 11.8
pip install torch==2.7.1 torchvision==0.22.1 torchaudio==2.7.1 --index-url https://download.pytorch.org/whl/cu118


# install the torchdec version matches to torch version and Python version
# https://github.com/meta-pytorch/torchcodec?tab=readme-ov-file#installing-torchcodec
pip install torchcodec==0.10.0

# install requirements
pip install -r requirements.txt

# install ffmpeg from https://ffmpeg.org/download.html
# add ffmpeg to PATH
```

