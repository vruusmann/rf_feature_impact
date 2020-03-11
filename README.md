# Installation #

Build using Apache Maven:

```
$ mvn clean package
```

The build produces an executable uber-JAR file `target/feature_impact-executable-1.0-SNAPSHOT.jar`.

# Development #

Initialize [Eclipse IDE](https://www.eclipse.org/ide/) project using Apache Maven:

```
$ mvn eclipse:eclipse
```

Import the project into Eclipse IDE using the menu path `File -> Import... -> General/Existing Projects into Workspace (-> Select root directory -> Finish)`.

The project should be now visible as `rf_feature_impact` under "Project explorer" and/or "Package explorer" views.

# Usage #

The resources folder `src/main/resources` contains two random forest examples.

Scoring the classification example:

```
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestClassifier.pmml --target-class 0 --csv-input src/main/resources/csv/Audit.csv --csv-output Audit-impact.csv
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestClassifier.pmml --target-class 1 --csv-input src/main/resources/csv/Audit.csv --csv-output Audit-impact.csv
```

Scoring the regression example:

```
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestRegressor.pmml --csv-input src/main/resources/csv/Auto.csv --csv-output Auto-impact.csv
```

Getting help:

```
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --help
```