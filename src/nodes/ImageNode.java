package nodes;

import engine.ExecutionContext;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageNode extends BaseNode {

    public String        imageName = "Image";
    public BufferedImage template  = null;
    public int           threshold = 85;

    public ImageNode(int x, int y) {
        super(NodeType.IMAGE, "Image", x, y);
        width = 160; height = 60;
        addOutputPort("Image");
    }

    @Override
    public String execute(ExecutionContext ctx) throws InterruptedException {
        // Image nodes just hold data — read by WatchCaseNode at runtime
        return "Image";
    }

    @Override public Color  nodeColor() { return new Color(80, 120, 80); }
    @Override public String nodeIcon()  { return "\u25a3"; }
}