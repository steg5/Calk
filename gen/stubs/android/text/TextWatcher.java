package android.text;
public interface TextWatcher {
    void beforeTextChanged(CharSequence s, int a, int b, int c);
    void onTextChanged(CharSequence s, int a, int b, int c);
    void afterTextChanged(Editable s);
}
