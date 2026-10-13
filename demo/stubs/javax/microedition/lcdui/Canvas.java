package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;

    protected Canvas() {}
    protected abstract void paint(Graphics g);
    public final void repaint() {}
    public final void serviceRepaints() {}
    public int getGameAction(int keyCode) { return 0; }
    protected void keyPressed(int keyCode) {}
    protected void keyReleased(int keyCode) {}
    protected void keyRepeated(int keyCode) {}
    public void setFullScreenMode(boolean mode) {}
}
