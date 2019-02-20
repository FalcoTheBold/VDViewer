package net.voidjinn.app.vdviewer;

import java.io.*;
import java.net.*;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.event.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.*;
import javafx.stage.*;
import net.voidjinn.lib.gltf4fx.ReaderGLTF;
import net.voidjinn.lib.vdfx.asset3d.asset.*;
import net.voidjinn.lib.vdfx.asset3d.importer.*;
import net.voidjinn.lib.vdfx.asset3d.scene.shape.Joint;

public class ViewerApp extends Application {

    public static final String NAME_TEMP_FILE = "vdfx_temp";
    //Function for getting Maven-resources
    //Example: this.getClass().getResource("/styles/style.css")

    private double mouseX = 0;
    private double mouseY = 0;
    private double mouseOldX = mouseX;
    private double mouseOldY = mouseY;
    private double offsetFromPoint = -700d;

    private final Translate pivot = new Translate(0, 0, 0);
    private final Rotate camRotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate camRotateY = new Rotate(0, Rotate.Y_AXIS);
    private final ObservableList<Node> actors = FXCollections.observableArrayList();

    //private GridPane gpContent = new GridPane();
    //private final Group group = new Group();
    private final BorderPane root3d = new BorderPane();
    private final TableView<Node> tvActors = new TableView<>(actors);
    private SubScene subScene;
    private PerspectiveCamera camera = new PerspectiveCamera(true);
    private Stage stage;
    private Scene mainScene;
    private final Spinner<Float> spnScale = new Spinner<>(-10f, 10f, 1f, 0.25f);
    private final TreeItem<String> tiRoot = new TreeItem<>("Skeletons");
    private final TreeView<String> tvArmatures = new TreeView<>(tiRoot);

    private final EventHandler<MouseEvent> pivotCamera = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent t) {
            double mouseDeltaX = mouseOldX - t.getX();
            double mouseDeltaY = mouseOldY - t.getY();
            mouseOldX = t.getX();
            mouseOldY = t.getY();
            if (t.isPrimaryButtonDown()) {
                pivot.setX(pivot.getX() + mouseDeltaX);
                pivot.setY(pivot.getY() + mouseDeltaY);
            }
            if (t.isSecondaryButtonDown()) {
                camRotateX.setAngle(camRotateX.getAngle() - mouseDeltaY);
                camRotateY.setAngle(camRotateY.getAngle() + mouseDeltaX);
            }
            if (t.isAltDown() && t.isSecondaryButtonDown()) {
                camera.setRotationAxis(Rotate.Z_AXIS);
                camera.setRotate(camera.getRotate() + mouseDeltaX);
            }
        }
    };

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        final BorderPane borderPane = new BorderPane();
        final Button btnOpen = new Button("Open");
        final Button btnLoad = new Button("Load");
        final HBox hbButtons = new HBox(btnOpen, btnLoad, spnScale);
        final ListView<Level> listView = new ListView<>(FXCollections.observableArrayList(createLevels()));
        listView.setCellFactory((ListView<Level> p) -> new ListCell<Level>() {
            @Override
            protected void updateItem(Level t, boolean bln) {
                if (t != null) {
                    super.updateItem(t, bln);
                    setText(t.getName());
                }
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends Level> ov, Level t, Level t1) -> {
            if (t != t1) {
                if (t1 != null) {
                    btnLoad.setDisable(false);
                } else {
                    btnLoad.setDisable(true);
                }
            }
        });
        btnOpen.setOnAction((ActionEvent event) -> {
            final FileChooser fc = new FileChooser();
            final File f = fc.showOpenDialog(ViewerApp.this.stage);
            if (f != null) {
                try {
                    Node result = loadModel(f.toURI().toURL());
                    result.setScaleX(10d);
                    result.setScaleY(10d);
                    result.setScaleZ(10d);
                    if (result != null) {
                        final Group newRoot = new Group(result);
                        root3d.getChildren().clear();
                        root3d.setCenter(newRoot);
                        //group.getChildren().clear();
                        //group.getChildren().add(newRoot);
                    }
                } catch (MalformedURLException ex) {
                    Logger.getLogger(ViewerApp.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                }
            }
        });
        btnLoad.setOnAction((ActionEvent t) -> {
            final Level lvl = listView.getSelectionModel().getSelectedItem();
            if (lvl != null) {
                final URL url = this.getClass().getResource("/" + lvl.getAssetPath());
                final Node result = loadModel(url);
                if (result != null) {
                    root3d.setCenter(result);
                } else {
                    System.out.println("No model loaded.");
                }
            }
        });
        final TableColumn<Node, String> tcNodeName = new TableColumn<>("ID");
        final TableColumn<Node, String> tcNodePosition = new TableColumn<>("Pos");
        tcNodeName.setCellValueFactory((TableColumn.CellDataFeatures<Node, String> p) -> p.getValue() != null ? p.getValue().idProperty() : new SimpleStringProperty("Faulty"));
        tcNodePosition.setCellValueFactory((TableColumn.CellDataFeatures<Node, String> p) -> Bindings.concat(p.getValue() != null ? p.getValue().translateXProperty() : new SimpleDoubleProperty(0))
                .concat("|")
                .concat(p.getValue() != null ? p.getValue().translateYProperty() : new SimpleDoubleProperty(0))
                .concat("|")
                .concat(p.getValue() != null ? p.getValue().translateZProperty() : new SimpleDoubleProperty(0)));
        tvActors.getColumns().addAll(tcNodeName, tcNodePosition);

        final Button btnFocus = new Button("Focus");
        btnFocus.disableProperty().bind(tvActors.getSelectionModel().selectedItemProperty().isNull());
        btnFocus.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                setCamTo(tvActors.getSelectionModel().getSelectedItem());
            }
        });
        final ToolBar tlbActors = new ToolBar(btnFocus);
        final VBox vbOverview = new VBox(tvActors, tlbActors);

        final Tab tbActors = new Tab("Actors", vbOverview);
        final Tab tbArmatures = new Tab("Armatures", tvArmatures);
        final TabPane tabPane = new TabPane(tbActors, tbArmatures);

        final PointLight light = new PointLight(Color.WHITE);
        light.setTranslateY(500);
        light.setTranslateX(0);
        camera.setNearClip(0.000001);
        camera.setFarClip(1000);
        camera.getTransforms().addAll(
                pivot,
                camRotateX,
                camRotateY
        );

        //final Group testGroup = new Group(new Label("Hallo"));
        //group.getChildren().addAll(light, testGroup);
        root3d.setTop(new Label("Hallo zum Level-Viewer!"));
        root3d.setCenter(new Label("Bitte waehle eine Map aus der Liste der Maps links aus."));
        subScene = new SubScene(root3d, 480, 360, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFill(Color.BLACK);
        subScene.setOnMouseDragged(pivotCamera);
        subScene.setOnMousePressed((MouseEvent t) -> {
            mouseOldX = t.getX();
            mouseOldY = t.getY();
        });
        subScene.setOnScroll((ScrollEvent t) -> {
            camera.setTranslateZ(camera.getTranslateZ() + t.getDeltaY());
        });
        //final Button btnTest = new Button("I am for testing!");
        //gpContent.addRow(1, btnTest);

        //lower informations
        final Label lblCameraPos = new Label();
        lblCameraPos.textProperty().bind(Bindings.concat("Camera - Pos: {")
                .concat(pivot.xProperty())
                .concat("/")
                .concat(pivot.yProperty())
                .concat("/")
                .concat(pivot.zProperty())
                .concat("}, Rot: {")
                .concat(camRotateX.angleProperty())
                .concat("|")
                .concat(camRotateY.angleProperty())
                .concat("}"));
        final Label lblTrade = new Label("@ VoiDjinn GmbH, 2018");
        lblTrade.setAlignment(Pos.CENTER_RIGHT);
        final HBox hbTrade = new HBox(lblTrade);
        final HBox hbCoordinates = new HBox(lblCameraPos);
        final HBox hbRoot = new HBox(hbCoordinates, hbTrade);
        hbTrade.setAlignment(Pos.CENTER_RIGHT);
        hbCoordinates.setAlignment(Pos.CENTER_LEFT);

        //Tools
        final Button btnCenter = new Button("Center");
        final ToolBar toolBar = new ToolBar(btnCenter);
        btnCenter.setOnAction((ActionEvent t) -> {
            centerCam();
        });

        final VBox vbLeft = new VBox(listView, hbButtons, tabPane);
        borderPane.setTop(toolBar);
        borderPane.setLeft(vbLeft);
        borderPane.setCenter(subScene);
        borderPane.setBottom(hbRoot);
        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        mainScene = new Scene(borderPane, bounds.getWidth() * 0.75, bounds.getHeight() * 0.75);
        stage.setTitle("VD - MapViewer");
        stage.setScene(mainScene);
        stage.show();
        centerCam();

        subScene.widthProperty().bind(stage.widthProperty().subtract(vbLeft.widthProperty()));
        subScene.heightProperty().bind(stage.heightProperty());
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    public void centerCam() {
        pivot.setX((root3d.getWidth() * 0.5));
        pivot.setY((root3d.getHeight() * 0.5));
        pivot.setZ(offsetFromPoint);
        camRotateX.setAngle(0);
        camRotateY.setAngle(0);
    }

    public void setCamTo(Node node) {
        pivot.setX(node.getTranslateX());
        pivot.setY(node.getTranslateY());
        pivot.setZ(node.getTranslateZ() + offsetFromPoint);
    }

    public Level[] createLevels() {
        final Level lvlFirst = new Level("Testroom", "/maps/testroom.obj");
        final Level lvlDS_Arena = new Level("Dragonslayer - Arena", "/maps/Arena_Wooden_Village.obj");
        //final Level lvlFirstGLTF = new Level("Testroom-GLTF", "/maps/testroom.gltf");
        //final Level lvlTown = new Level("Valrhuk", "/maps/valrhuk.obj");
        final Level[] setupLevels = new Level[]{
            lvlFirst,
            lvlDS_Arena
        };
        return setupLevels;
    }

    private Node loadModel(URL url) {
        //Axis-Format should be:
        //Forward:   Z
        //Up:       -Y     
        try {
            Group result = new Group();
            final String uriExt = url.toURI().toString().substring(url.toURI().toString().lastIndexOf(".") + 1);
            Importer importer = null;
            switch (Level.FileType.lookValidFormat(uriExt)) {
                /*
                case OBJ:
                  importer = new WavefrontObjImporter();
                  break;
                 */
                case GLTF:
                    ReaderGLTF readerGLTF = new ReaderGLTF();
                    readerGLTF.load(url);
                    //readerGLTF.read(url);
                    //importer = new ImporterGltf();
                    break;
            }
            if (importer != null) {
                importer.setScale(1f);
                final ImporterData data = importer.load(url);
                final AssetBuilderFX builderFX = new AssetBuilderFX();
                Node buildResult = builderFX.build(data);
                if (data.isWholeScene()) {
                    result = (Group) buildResult;
                    result.getChildren().forEach((node) -> {
                        addToActors(node);
                    });
                } else {
                    result.getChildren().add(buildResult);
                    addToActors(buildResult);
                }
                if (data.getArmatures().size() > 0) {
                    for (AssetArmature value : data.getArmatures().values()) {
                        //Take first element as new root, then proccess childrens
                        final Joint forestRoot = (Joint) value.getJointForest().get(0);
                        final TreeItem<String> newRoot = new TreeItem<>(forestRoot.getId());
                        tiRoot.getChildren().add(newRoot);
                        buildAsTreeView(forestRoot, newRoot);
                        result.getChildren().addAll(value.getJointForest());
                    }
                }
            }
            return result;
        } catch (URISyntaxException | IOException ex) {
            Logger.getLogger(ViewerApp.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        return null;
    }

    private void addToActors(Node actor) {
        actor.setOnMouseClicked((MouseEvent t) -> {
            pivot.setX(t.getX());
            pivot.setY(t.getY());
            pivot.setZ(t.getZ());
        });
        actors.add(actor);
    }

    private void buildAsTreeView(final Parent joint, TreeItem<String> corItem) {
        joint.getChildrenUnmodifiable().stream().filter((node) -> (node.getClass().isAssignableFrom(Joint.class))).map((node) -> (Joint) node).forEachOrdered((childJoint) -> {
            final TreeItem<String> newJoint = new TreeItem<>(childJoint.getId());
            corItem.getChildren().add(newJoint);
            buildAsTreeView(childJoint, newJoint);
        });
    }

}
