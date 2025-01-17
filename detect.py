import cv2
import numpy as np
import os
import supervision as sv
from roboflow import Roboflow
from PIL import Image, ImageDraw, ImageFont
import stat

def ensure_jpg(image_path):
    if image_path.lower().endswith('.png'):
        img = cv2.imread(image_path)
        jpg_path = image_path.rsplit('.', 1)[0] + '.jpg'
        cv2.imwrite(jpg_path, img)
        return jpg_path
    return image_path

def create_label(text, confidence, font_size=12):
    try:
        font = ImageFont.truetype("arial.ttf", font_size)
    except OSError:
        font = ImageFont.load_default()
    
    label_text = f"{text}.) {confidence:.0f}%"
    bbox = font.getbbox(label_text)
    padding = 3
    label_width = bbox[2] - bbox[0] + 2 * padding
    label_height = bbox[3] - bbox[1] + 2 * padding
    
    label_img = Image.new('RGB', (label_width, label_height), (255, 255, 0))
    draw = ImageDraw.Draw(label_img)
    draw.text((padding, padding), label_text, font=font, fill=(0, 0, 0))
    return np.array(label_img)

def process_crosswalk(image, mask, detection_num, confidence, save_path):
    y_indices, x_indices = np.where(mask > 0)
    if not len(y_indices): return None
    
    top, bottom = np.min(y_indices), np.max(y_indices)
    left, right = np.min(x_indices), np.max(x_indices)
    cropped_image = image[top:bottom+1, left:right+1].copy()
    cropped_mask = mask[top:bottom+1, left:right+1].copy()
    
    gray = cv2.cvtColor(cropped_image, cv2.COLOR_BGR2GRAY)
    rgba = np.zeros((*gray.shape, 4), dtype=np.uint8)
    rgba[..., :3] = cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR) 
    rgba[..., 3] = cropped_mask * 255 
    
    crosswalk_path = os.path.join(save_path, f"{detection_num}.")
    os.makedirs(crosswalk_path, exist_ok=True)
    os.chmod(crosswalk_path, stat.S_IRWXU | stat.S_IRWXG | stat.S_IRWXO)
    
    cv2.imwrite(os.path.join(crosswalk_path, f"{detection_num}.png"), rgba)
    
    height, width = cropped_mask.shape
    txt_path = os.path.join(crosswalk_path, f"{detection_num}.txt")
    with open(txt_path, "w") as f:
        f.write(f"{int(confidence * 100)} {width} " + " ".join(
            str(gray[y,x]) if cropped_mask[y,x] > 0 else "-1"
            for y in range(height)
            for x in range(width)
        ))
    os.chmod(txt_path, stat.S_IRWXU | stat.S_IRWXG | stat.S_IRWXO)
    
    return cropped_mask

def main(image_path, parliecibas_slieksnis=1):

    print()
    print(f"Apstrādā: {image_path}")
    print()
    
    image_path = ensure_jpg(image_path)
    
    image_name = os.path.splitext(os.path.basename(image_path))[0]
    save_path = os.path.join("Results", f"Results_{image_name}")
    os.makedirs(save_path, exist_ok=True)
    os.chmod(save_path, stat.S_IRWXU | stat.S_IRWXG | stat.S_IRWXO)
    
    rf = Roboflow(api_key="jNrxr7no6M93b3IjpBTa")
    model = rf.workspace().project("seg_size_crosswalk_bump").version(1).model
    
    result = model.predict(image_path, confidence=parliecibas_slieksnis).json()
    detections = sv.Detections.from_inference(result)
    
    image = cv2.imread(image_path)
    annotated_image = image.copy()
    
    for i, (xyxy, mask, confidence) in enumerate(zip(detections.xyxy, detections.mask, detections.confidence)):
        if mask is not None:
            cv2.drawContours(annotated_image, 
                           cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, 
                                          cv2.CHAIN_APPROX_SIMPLE)[0], 
                           -1, (255, 255, 0), 2)
            
            process_crosswalk(image, mask, i+1, confidence, save_path)
            
            label = create_label(str(i+1), confidence * 100)
            x1, y1 = map(int, xyxy[:2])
            h, w = label.shape[:2]
            annotated_image[y1:y1+h, x1:x1+w] = label
    
    segmented_path = os.path.join(save_path, f"segmented_{image_name}.png")
    cv2.imwrite(segmented_path, annotated_image)
    print()
    os.chmod(segmented_path, stat.S_IRWXU | stat.S_IRWXG | stat.S_IRWXO)
    print(f"Rezultāti saglabāti: {save_path}")
    print()

if __name__ == "__main__":

    #Bezpilota lidaparāta attēls / simulētais attēls
    main("Simulated_images/1..png", parliecibas_slieksnis=1)