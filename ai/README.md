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

# CUDA 11.8
pip install torch==2.7.1 torchvision==0.22.1 torchaudio==2.7.1 --index-url https://download.pytorch.org/whl/cu118
```

