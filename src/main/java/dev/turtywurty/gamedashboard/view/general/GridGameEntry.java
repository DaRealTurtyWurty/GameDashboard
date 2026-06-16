package dev.turtywurty.gamedashboard.view.general;

import dev.turtywurty.gamedashboard.data.game.Game;
import dev.turtywurty.gamedashboard.util.ImageCache;
import dev.turtywurty.gamedashboard.util.Utils;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.animation.FillTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class GridGameEntry {
    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final double TILE_WIDTH = 150;
    private static final double TILE_HEIGHT = 200;
    private static final double PLATFORM_BADGE_SIZE = 30;
    private static final double PLATFORM_ICON_SIZE = 24;
    private static final double PLATFORM_ICON_SOURCE_SIZE = 96;

    private final StackPane node;
    private final Tile tile;
    private final Game game;

    private final ObjectProperty<Runnable> onEnter = new SimpleObjectProperty<>();
    private final ObjectProperty<Runnable> onExit = new SimpleObjectProperty<>();

    public GridGameEntry(Game game) {
        this.game = game;

        this.tile = TileBuilder.create()
                .skinType(Tile.SkinType.CUSTOM)
                .prefSize(TILE_WIDTH, TILE_HEIGHT)
                .textAlignment(TextAlignment.CENTER)
                .text(game.getTitle())
                .textSize(Tile.TextSize.BIGGER)
                .roundedCorners(true)
                .backgroundImage(ImageCache.getImage(game.getCoverImageURL()))
                .backgroundImageOpacity(1)
                .build();
        tile.setUserData(game);

        this.tile.setBackgroundImage(applyBottomGradient(this.tile.getBackgroundImage()));
        this.node = createNode();

        this.tile.setOnMouseClicked(event -> {
            String command = game.getExecutionCommand();
            if (command == null)
                return;

            try {
                List<ProcessHandle> processes = ProcessHandle.allProcesses().toList();

                Runtime.getRuntime().exec(command);

                // iconify
                Stage stage = ((Stage) getTile().getScene().getWindow());
                stage.hide();
                stage.setIconified(true);

                if (SystemTray.isSupported()) {
                    SystemTray tray = SystemTray.getSystemTray();
                    TrayIcon icon = createTrayIcon(game, stage, tray);

                    // check if game is still running
                    executorService.scheduleAtFixedRate(() -> {
                        List<ProcessHandle> newProcesses = ProcessHandle.allProcesses().collect(Collectors.toList());
                        newProcesses.removeAll(processes);

                        if (newProcesses.isEmpty()) {
                            Platform.runLater(() -> {
                                stage.show();
                                stage.setIconified(false);
                                tray.remove(icon);

                                executorService.shutdownNow();
                                executorService.close();
                                executorService = Executors.newSingleThreadScheduledExecutor();
                                System.gc();
                            });

                            return;
                        }

                        System.out.println("Processes: " + newProcesses.stream()
                                .map(process -> process.info().command().orElse(""))
                                .collect(Collectors.joining(", ")));

                        game.findPossibleProcess(newProcesses).ifPresent(process -> {
                            Platform.runLater(() -> {
                                stage.show();
                                stage.setIconified(false);
                                tray.remove(icon);

                                executorService.shutdownNow();
                                executorService.close();
                                executorService = Executors.newSingleThreadScheduledExecutor();
                                System.gc();
                            });
                        });
                    }, 5, 1, TimeUnit.SECONDS);
                }
            } catch (AWTException exception) {
                throw new RuntimeException("Unable to add tray icon", exception);
            } catch (IOException exception) {
                throw new RuntimeException("Unable to execute game", exception);
            }
        });

        var scaleUp = new ScaleTransition(Duration.millis(50), this.node);
        scaleUp.setFromX(1);
        scaleUp.setFromY(1);
        scaleUp.setToX(1.1);
        scaleUp.setToY(1.1);

        var scaleDown = new ScaleTransition(Duration.millis(50), this.node);
        scaleDown.setFromX(1.1);
        scaleDown.setFromY(1.1);
        scaleDown.setToX(1);
        scaleDown.setToY(1);

        List<Runnable> enterActions = new ArrayList<>(List.of(
                () -> this.tile.setEffect(new Glow()),
                scaleUp::play,
                () -> {
                    Runnable onEnter = this.onEnter.get();
                    if (onEnter != null)
                        onEnter.run();
                }
        ));

        List<Runnable> exitActions = new ArrayList<>(List.of(
                () -> this.tile.setEffect(null),
                scaleDown::play,
                () -> {
                    Runnable onExit = this.onExit.get();
                    if (onExit != null)
                        onExit.run();
                }
        ));

        this.tile.setOnMouseEntered(event -> {
            for (Runnable action : enterActions)
                action.run();
        });

        this.tile.setOnMouseExited(event -> {
            for (Runnable action : exitActions)
                action.run();
        });

        this.node.parentProperty().addListener(new ParentChangedListener());
    }

    private StackPane createNode() {
        var container = new StackPane(this.tile);
        container.setPrefSize(TILE_WIDTH, TILE_HEIGHT);
        container.setMinSize(TILE_WIDTH, TILE_HEIGHT);
        container.setMaxSize(TILE_WIDTH, TILE_HEIGHT);
        container.setUserData(this.game);

        Node platformBadge = createPlatformBadge();
        if (platformBadge != null) {
            container.getChildren().add(platformBadge);
            StackPane.setAlignment(platformBadge, Pos.TOP_RIGHT);
            StackPane.setMargin(platformBadge, new Insets(8, 8, 0, 0));
        }

        return container;
    }

    private Node createPlatformBadge() {
        String iconPath = switch (this.game.getType()) {
            case "steam" -> "/images/platforms/steam.png";
            case "epic_games" -> "/images/platforms/epic_games.png";
            default -> null;
        };
        if (iconPath == null)
            return null;

        var icon = new ImageView(new Image(
                getClass().getResource(iconPath).toExternalForm(),
                PLATFORM_ICON_SOURCE_SIZE,
                PLATFORM_ICON_SOURCE_SIZE,
                true,
                true
        ));
        icon.setFitWidth(PLATFORM_ICON_SIZE);
        icon.setFitHeight(PLATFORM_ICON_SIZE);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setCache(false);
        icon.setClip(new Circle(PLATFORM_ICON_SIZE / 2, PLATFORM_ICON_SIZE / 2, PLATFORM_ICON_SIZE / 2));
        icon.setMouseTransparent(true);

        var badge = new StackPane(icon);
        badge.getStyleClass().add("platform-badge");
        badge.setPrefSize(PLATFORM_BADGE_SIZE, PLATFORM_BADGE_SIZE);
        badge.setMinSize(PLATFORM_BADGE_SIZE, PLATFORM_BADGE_SIZE);
        badge.setMaxSize(PLATFORM_BADGE_SIZE, PLATFORM_BADGE_SIZE);
        badge.setClip(new Circle(
                PLATFORM_BADGE_SIZE / 2,
                PLATFORM_BADGE_SIZE / 2,
                PLATFORM_BADGE_SIZE / 2
        ));
        badge.setMouseTransparent(true);
        return badge;
    }

    private static Image applyBottomGradient(Image sourceImage) {
        BufferedImage image = SwingFXUtils.fromFXImage(sourceImage, null);
        if (image == null)
            return sourceImage;

        BufferedImage gradient = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = gradient.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.setPaint(new GradientPaint(
                0, image.getHeight() / 2f,
                new Color(0, 0, 0, 0),
                0, image.getHeight(),
                new Color(0, 0, 0, 255)));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return SwingFXUtils.toFXImage(gradient, null);
    }

    private static @NotNull TrayIcon createTrayIcon(Game game, Stage stage, SystemTray tray) throws AWTException {
        TrayIcon icon = new TrayIcon(SwingFXUtils.fromFXImage(
                ImageCache.getImage(game.getCoverImageURL()),
                new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)));
        icon.setImageAutoSize(true);
        icon.setToolTip("Playing " + game.getTitle() + "...");

        var menu = createPopupMenu(stage, tray, icon);
        icon.setPopupMenu(menu);

        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1)
                    return;

                Platform.runLater(() -> {
                    stage.show();
                    stage.setIconified(false);
                    tray.remove(icon);
                });
            }
        });

        tray.add(icon);
        return icon;
    }

    private static @NotNull PopupMenu createPopupMenu(Stage stage, SystemTray tray, TrayIcon icon) {
        var menu = new PopupMenu();
        MenuItem exitItem = new MenuItem("Exit");
        MenuItem showItem = new MenuItem("Show");
        exitItem.addActionListener(ignored -> {
            Platform.runLater(() -> {
                stage.close();
                tray.remove(icon);
                Platform.exit();
            });
        });

        showItem.addActionListener(ignored -> {
            Platform.runLater(() -> {
                stage.show();
                stage.setIconified(false);
                tray.remove(icon);
            });
        });

        menu.add(exitItem);
        menu.add(showItem);
        return menu;
    }

    public class ParentChangedListener implements ChangeListener<Parent> {
        private ParentChangedListener() {
            changed(GridGameEntry.this.node.parentProperty(), null, GridGameEntry.this.node.getParent());
        }

        @Override
        public void changed(ObservableValue<? extends Parent> observable, Parent oldValue, Parent newValue) {
            if (newValue == null)
                return;

            if (!(newValue instanceof Pane paneParent) || paneParent.getBackground() == null || paneParent.getBackground().getFills().isEmpty())
                return;

            Background background = paneParent.getBackground();

            Runnable enterAction = () -> {
                Rectangle shape = new Rectangle();
                shape.setFill(paneParent.getBackground().getFills().getFirst().getFill());

                var transition = new FillTransition();
                transition.setShape(shape);
                transition.setDuration(Duration.millis(500));
                transition.setDelay(Duration.millis(10));
                transition.setFromValue((javafx.scene.paint.Color) paneParent.getBackground().getFills().getFirst().getFill());
                transition.setToValue(Utils.getAverageColor(GridGameEntry.this.tile.getBackgroundImage()));

                transition.setInterpolator(new Interpolator() {
                    @Override
                    protected double curve(double t) {
                        paneParent.setBackground(Utils.createBackground((javafx.scene.paint.Color) shape.getFill()));
                        return t;
                    }
                });

                transition.play();
            };

            Runnable exitAction = () -> {
                Rectangle shape = new Rectangle();
                shape.setFill(paneParent.getBackground().getFills().getFirst().getFill());

                var transition = new FillTransition();
                transition.setShape(shape);
                transition.setDuration(Duration.millis(500));
                transition.setFromValue((javafx.scene.paint.Color) paneParent.getBackground().getFills().getFirst().getFill());
                transition.setToValue((javafx.scene.paint.Color) background.getFills().getFirst().getFill());

                transition.setInterpolator(new Interpolator() {
                    @Override
                    protected double curve(double t) {
                        paneParent.setBackground(Utils.createBackground((javafx.scene.paint.Color) shape.getFill()));
                        return t;
                    }
                });

                transition.play();
            };

            GridGameEntry.this.onEnter.set(enterAction);
            GridGameEntry.this.onExit.set(exitAction);
        }
    }
}
