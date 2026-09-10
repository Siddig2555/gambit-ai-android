# Gambit V12 Lite — Google Colab

1. من Android صدّر `gambit_training.jsonl`.
2. افتح هذا السكربت في Google Colab.
3. شغّله وارفع الملف عند ظهور نافذة Upload.
4. بعد التدريب سيظهر `gambit_models.zip`.
5. فك الضغط وخذ:
   - `lstm.onnx`
   - `transformer.onnx`
   - `dynamic.onnx`
6. ضعها في:
   `app/src/main/assets/models/`
7. أعد بناء APK.

مهم:
هذه النسخة تنقل التدريب الثقيل إلى Colab، وتستخدم نماذج أصغر من V12 الأصلي لتناسب Android. لا تحاول تشغيل backpropagation أو إعادة بناء LSTM على الهاتف.
