import av
import torch
import numpy as np

from transformers import AutoImageProcessor, TimesformerForVideoClassification
from huggingface_hub import hf_hub_download

# 设置随机种子，确保结果可重复
np.random.seed(0)


def read_video_pyav(container, indices):
    """
    使用 PyAV 解码器解码视频。
    参数:
        container (`av.container.input.InputContainer`): PyAV 视频容器对象。
        indices (`List[int]`): 需要解码的帧索引列表。
    返回:
        result (np.ndarray): 解码后的帧构成的 numpy 数组，形状为 (num_frames, height, width, 3)。
    """
    frames = []  # 用于存储解码后的帧
    container.seek(0)  # 将视频位置设置为起始位置
    start_index = indices[0]  # 解码的起始帧索引
    end_index = indices[-1]  # 解码的结束帧索引
    for i, frame in enumerate(container.decode(video=0)):  # 遍历视频帧
        if i > end_index:  # 如果当前帧索引超过结束帧索引，停止解码
            break
        if i >= start_index and i in indices:  # 如果当前帧在需要解码的索引范围内
            frames.append(frame)  # 将帧添加到帧列表
    # 将帧转换为 numpy 数组，并返回
    return np.stack([x.to_ndarray(format="rgb24") for x in frames])


def sample_frame_indices(clip_len, frame_sample_rate, seg_len):
    """
    采样视频帧的索引。
    参数:
        clip_len (int): 需要采样的帧数量。
        frame_sample_rate (int): 帧采样率。
        seg_len (int): 视频总帧数。
    返回:
        indices (np.ndarray): 采样的帧索引。
    """
    # 根据采样率计算采样帧的总长度
    converted_len = int(clip_len * frame_sample_rate)
    # 随机选择一个结束帧索引，确保其小于视频总帧数
    end_idx = np.random.randint(converted_len, seg_len)
    # 起始帧索引 = 结束帧索引 - 转换后的采样帧长度
    start_idx = end_idx - converted_len
    # 生成等间距的帧索引，并裁剪到有效范围
    indices = np.linspace(start_idx, end_idx, num=clip_len)
    indices = np.clip(indices, start_idx, end_idx - 1).astype(np.int64)
    return indices


# 加载视频：视频片段包含 300 帧（10 秒，30 FPS）
file_path = hf_hub_download(
    repo_id="nielsr/video-demo", filename="eating_spaghetti.mp4", repo_type="dataset"
)  # 从 Hugging Face Hub 下载示例视频
container = av.open(file_path)  # 用 PyAV 打开视频文件

# 采样 8 帧
indices = sample_frame_indices(clip_len=8, frame_sample_rate=1, seg_len=container.streams.video[0].frames)
video = read_video_pyav(container, indices)  # 根据采样索引解码视频帧

# 加载图像处理器和预训练的视频分类模型
image_processor = AutoImageProcessor.from_pretrained("MCG-NJU/videomae-base-finetuned-kinetics")
model = TimesformerForVideoClassification.from_pretrained("facebook/timesformer-base-finetuned-k400")

# 使用图像处理器对视频帧进行预处理
inputs = image_processor(list(video), return_tensors="pt")

# 禁用梯度计算，进行推理
with torch.no_grad():
    outputs = model(**inputs)  # 前向传播，得到模型输出
    logits = outputs.logits  # 输出的 logits，表示对每个类别的未归一化分数

# 模型预测结果是 400 个 Kinetics-400 类别之一
predicted_label = logits.argmax(-1).item()  # 获取分数最大的类别索引
print(model.config.id2label[predicted_label])  # 打印预测类别的标签