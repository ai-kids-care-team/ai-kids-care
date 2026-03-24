import av
import numpy as np
import torch
from transformers import VideoMAEVideoProcessor, VideoMAEForVideoClassification
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

video_processor = VideoMAEVideoProcessor.from_pretrained("MCG-NJU/videomae-base-finetuned-kinetics")
model = VideoMAEForVideoClassification.from_pretrained("MCG-NJU/videomae-base-finetuned-kinetics")

# get total frame count
container = av.open(video_path)
total_frames = container.streams.video[0].frames
container.close()

# sample 16 frames
indices = sample_frame_indices(16, total_frames)
frames = read_video_pyav(video_path, indices)

inputs = video_processor(frames, return_tensors="pt")

with torch.no_grad():
    outputs = model(**inputs)
    logits = outputs.logits

# model predicts one of the 400 Kinetics-400 classes
predicted_label = logits.argmax(-1).item()
print(model.config.id2label[predicted_label])
