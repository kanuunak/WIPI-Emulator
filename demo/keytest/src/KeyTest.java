// KeyTest — 입력 경로 검증용 MIDlet (MIT, Fold Retro Phone 포크 자체 제작)
// 화면: P(눌림)/R(반복)/U(뗌) 누적 수, 현재 눌린 키 수(HELD), 최근 이벤트 13개.
// 모든 손가락을 뗐는데 HELD가 0이 아니면 '키 고착' 버그.
// RustJava 제약: 문자열 '+' 연결 금지(StringBuilder 미구현) → StringBuffer만 사용.
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class KeyTest extends MIDlet {
    private Screen screen;

    protected void startApp() {
        if (screen == null) {
            screen = new Screen();
            Display.getDisplay(this).setCurrent(screen);
        }
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    static class Screen extends Canvas {
        private final String[] log = new String[13];
        private final int[] held = new int[32];
        private int heldCount = 0;
        private int presses = 0, repeats = 0, releases = 0;

        private void add(char type, int code) {
            StringBuffer sb = new StringBuffer();
            sb.append(type).append(' ').append(code).append("  ga=").append(getGameAction(code));
            for (int i = log.length - 1; i > 0; i--) log[i] = log[i - 1];
            log[0] = sb.toString();
            repaint();
        }

        private void hold(int code) {
            for (int i = 0; i < heldCount; i++) if (held[i] == code) return;
            if (heldCount < held.length) held[heldCount++] = code;
        }

        private void unhold(int code) {
            for (int i = 0; i < heldCount; i++) {
                if (held[i] == code) {
                    held[i] = held[--heldCount];
                    return;
                }
            }
        }

        protected void keyPressed(int c) { presses++; hold(c); add('P', c); }
        protected void keyRepeated(int c) { repeats++; add('R', c); }
        protected void keyReleased(int c) { releases++; unhold(c); add('U', c); }

        protected void paint(Graphics g) {
            g.setColor(0x000000);
            g.fillRect(0, 0, 240, 320);
            g.setColor(heldCount == 0 ? 0x80FF80 : 0xFFD040);
            StringBuffer h = new StringBuffer();
            h.append("P:").append(presses).append(" R:").append(repeats).append(" U:").append(releases).append(" HELD:").append(heldCount);
            g.drawString("KEY TEST", 4, 2, Graphics.TOP | Graphics.LEFT);
            g.drawString(h.toString(), 4, 20, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xFFFFFF);
            for (int i = 0; i < log.length; i++) {
                if (log[i] != null) g.drawString(log[i], 4, 44 + i * 21, Graphics.TOP | Graphics.LEFT);
            }
        }
    }
}
