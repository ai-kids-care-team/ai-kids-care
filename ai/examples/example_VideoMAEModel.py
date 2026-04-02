import av
import numpy as np
import torch
from transformers import VideoMAEVideoProcessor, VideoMAEModel
from huggingface_hub import hf_hub_download


def read_video_pyav(video_path, indices):
    container = av.open(video_path)
    frames = []
    start_index = indices[0]
    end_index = indices[-1]

    for i, frame in enumerate(container.decode(video=0)):
        if i > end_index:
            break
        if i >= start_index and i in indices:
            frames.append(frame.to_ndarray(format="rgb24"))

    return frames


def sample_frame_indices(num_frames_to_sample, total_frames):
    # 均匀采样
    return np.linspace(0, total_frames - 1, num_frames_to_sample).astype(int)


# replace this with your own video file
video_path = hf_hub_download(
    repo_id="nielsr/video-demo", filename="eating_spaghetti.mp4", repo_type="dataset"
)

video_processor = VideoMAEVideoProcessor.from_pretrained("MCG-NJU/videomae-base")
model = VideoMAEModel.from_pretrained("MCG-NJU/videomae-base")

# get total frame count
container = av.open(video_path)
total_frames = container.streams.video[0].frames
container.close()

# sample 16 frames
indices = sample_frame_indices(16, total_frames)
frames = read_video_pyav(video_path, indices)

# prepare video for the model
inputs = video_processor(frames, return_tensors="pt")

# forward pass
with torch.no_grad():
    outputs = model(**inputs)

last_hidden_states = outputs.last_hidden_state
print(list(last_hidden_states.shape))
