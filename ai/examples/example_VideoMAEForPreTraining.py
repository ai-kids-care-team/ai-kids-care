from transformers import AutoImageProcessor, VideoMAEForPreTraining
import numpy as np
import torch

num_frames = 16
video = list(np.random.randint(0, 256, (num_frames, 3, 224, 224)))

image_processor = AutoImageProcessor.from_pretrained("MCG-NJU/videomae-base")
model = VideoMAEForPreTraining.from_pretrained("MCG-NJU/videomae-base")

pixel_values = image_processor(video, return_tensors="pt").pixel_values

num_patches_per_frame = (model.config.image_size // model.config.patch_size) ** 2
seq_length = (num_frames // model.config.tubelet_size) * num_patches_per_frame
bool_masked_pos = torch.randint(0, 2, (1, seq_length)).bool()

outputs = model(pixel_values, bool_masked_pos=bool_masked_pos)
loss = outputs.loss