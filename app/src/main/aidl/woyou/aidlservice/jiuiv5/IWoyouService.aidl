package woyou.aidlservice.jiuiv5;
import woyou.aidlservice.jiuiv5.ICallback;

interface IWoyouService {
    void printerInit(in ICallback callback);
    void printerSelfChecking(in ICallback callback);
    String getPrinterSerialNo();
    String getPrinterModal();
    String getPrinterVersion();
    void lineWrap(int n, in ICallback callback);
    void printText(String text, in ICallback callback);
    void printTextWithFont(String text, String typeface, float fontsize, in ICallback callback);
    void printAndFeedPaper(int distance, in ICallback callback);
    void cutPaper(in ICallback callback);
    void openDrawer(in ICallback callback);
    int updatePrinterState();
}
