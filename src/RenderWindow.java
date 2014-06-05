import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

final public class RenderWindow extends JFrame {
    public int frameWidth;                     // Screen Width
    public int frameHeight;
    private int xPos;
    private int yPos;

    private BufferedImage image;
    private Graphics2D g;
    private BufferStrategy strategy;

    // Public buffer for other threads to manipulate
    int[] pixelData;

    // Constructor
    RenderWindow(int width, int height, int x, int y) {
        frameWidth = width;
        frameHeight = height;
        xPos = x;
        yPos = y;

        image = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB);
        pixelData = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        setSize(frameWidth, frameHeight);
        this.setLocation(xPos, yPos);
        show();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("CB Live Demo");
        createBufferStrategy(2);
        strategy = getBufferStrategy();

    }

    public void drawFrame() {
        // Get hold of a graphics context for the accelerated surface
        g = (Graphics2D) strategy.getDrawGraphics();
        g.drawImage(image, 0, 0, null);
        strategy.show();
    }

};