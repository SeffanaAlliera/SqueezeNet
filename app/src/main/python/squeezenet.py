import torch
import torchvision.transforms as transforms
from PIL import Image
from io import BytesIO
import os

# Get the path to the model file in the assets folder
model_path = os.path.join(os.path.dirname(__file__), "catsdogsmodel.pt")

# Load the converted TorchScript model
model = torch.jit.load(model_path)

# Define transformation for test data
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])

# Define class labels
class_labels = ['Cat', 'Dog']

# Function to predict the class and show confidence scores
def predict(image_bytes):
    image = Image.open(BytesIO(image_bytes))
    image = transform(image).unsqueeze(0)
    with torch.no_grad():
        outputs = model(image)
    probabilities = torch.softmax(outputs, dim=1)[0]
    predicted_class = torch.argmax(probabilities).item()

    # Convert probabilities to percentage
    confidence_scores = {class_labels[i]: prob.item() * 100 for i, prob in enumerate(probabilities)}

    # Get the predicted class label
    predicted_label = class_labels[predicted_class]

    # Format the result
    result_str = f"Predicted Class: {predicted_label}\n"

    # Append confidence scores for all classes
    for label, confidence in confidence_scores.items():
        result_str += f"{label}: {confidence:.2f}%\n"

    return result_str

