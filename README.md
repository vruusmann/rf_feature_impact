# Installation #

Build using Apache Maven:

```
$ mvn clean package
```

The build produces an executable uber-JAR file `target/demo-executable-1.0-SNAPSHOT.jar`.

# Development #

Initialize [Eclipse IDE](https://www.eclipse.org/ide/) project using Apache Maven:

```
$ mvn eclipse:eclipse
```

Import the project into Eclipse IDE using the menu path `File -> Import... -> General/Existing Projects into Workspace (-> Select root directory -> Finish)`.

The project should be now visible as `rf_feature_impact` under "Project explorer" and/or "Package explorer" views.

# Usage #

Run Python script:

```
$ python main.py
```

The training session produces two PMML files `Classifier.pmml` and `CompactClassifier.pmml`, and a CSV file `demo_X.csv`.

Score PMML files with the CSV file:

```
$ java -jar target/demo-executable-1.0-SNAPSHOT.jar Classifier.pmml demo_X.csv
$ java -jar target/demo-executable-1.0-SNAPSHOT.jar CompactClassifier.pmml demo_X.csv
```