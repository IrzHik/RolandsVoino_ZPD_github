from roboflow import Roboflow
import supervision as sv
import cv2
import os
import glob
from PIL import Image
import pandas as pd
from tqdm import tqdm

# Noteikšanas ciklu skaits
cycles = 3 

def convert_to_jpg(png_path):
    jpg_path = png_path.replace('.png', '.jpg')
    if not os.path.exists(jpg_path):
        image = Image.open(png_path)
        rgb_image = image.convert('RGB')
        rgb_image.save(jpg_path)
    return jpg_path

def process_image(image_path, model, cycles):
    confidence_scores = []
    for _ in range(cycles):
        result = model.predict(image_path, confidence=0).json()
        crosswalk_predictions = [pred for pred in result["predictions"] 
                               if pred["class"] == "crosswalk"]
        if crosswalk_predictions:
            score = max(pred.get("confidence", 0) for pred in crosswalk_predictions)
        else:
            score = 0
        confidence_scores.append(score)
        del result
    return confidence_scores

rf = Roboflow(api_key="jNrxr7no6M93b3IjpBTa")
project = rf.workspace().project("seg_size_crosswalk_bump")
model = project.version(1).model

directory = "Optimal height/Simulated_images"
png_files = glob.glob(f"{directory}/GPA*.png")
results = []

total_operations = len(png_files)
progress_bar = tqdm(total=total_operations, desc=f"Apstrādā attēlus ({cycles} cikli")

for png_path in png_files:
    jpg_path = convert_to_jpg(png_path)
    image_number = png_path.split('GPA')[-1].split('.')[0]
    
    confidence_scores = process_image(jpg_path, model, cycles)
    
    result_dict = {'Image_Number': image_number}
    for i, score in enumerate(confidence_scores, 1):
        result_dict[f'Confidence_{i}'] = f"{score:.4f}"
    
    results.append(result_dict)
    progress_bar.update(1)

progress_bar.close()

df = pd.DataFrame(results)
df = df.sort_values('Image_Number', key=lambda x: x.astype(float))

print("\nExcel-compatible output (tab-separated):")
header = "\t".join(df.columns)
print(header)
for _, row in df.iterrows():
    print("\t".join(row.values))