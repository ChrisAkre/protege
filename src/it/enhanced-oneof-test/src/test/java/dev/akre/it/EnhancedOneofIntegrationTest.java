package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import dev.akre.it.EnhancedOneofProtos.Thing;

public class EnhancedOneofIntegrationTest {

    @Test
    public void testEnhancedOneofSwitch() {
        Thing circle = Thing.newBuilder()
            .setCircle(Thing.Circle.newBuilder().setRadius(5.0).build())
            .build();
        assertThat(calculateArea(circle)).isEqualTo(Math.PI * 25.0);

        Thing rectangle = Thing.newBuilder()
            .setRectangle(Thing.Rectangle.newBuilder().setWidth(4.0).setHeight(5.0).build())
            .build();
        assertThat(calculateArea(rectangle)).isEqualTo(20.0);

        Thing  square = Thing .newBuilder()
                .setSquare(Thing.Square.newBuilder().setSide(3.0).build())
                .build();
        assertThat(calculateArea(square)).isEqualTo(9.0);

        Thing empty = Thing.newBuilder().build();
        assertThat(calculateArea(empty)).isEqualTo(0.0);
    }

    private double calculateArea(Thing thing) {
        return switch (thing.getShape()) {
            case Thing.CircleOrBuilder c -> Math.PI * c.getRadius() * c.getRadius();
            case Thing.RectangleOrBuilder r -> r.getWidth() * r.getHeight();
            case Thing.SquareOrBuilder s -> s.getSide() * s.getSide();
            case null -> 0.0;
        };
    }
}
