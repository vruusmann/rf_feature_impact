# About #

A small command-line Java application for analyzing feature impact in decision tree and decision tree ensemble models.

Description of the algorithm:

* [WHY did your model predict THAT? (Part 1 of 2)](https://towardsdatascience.com/why-did-your-model-predict-that-4f7ed3526397)
* [WHY did your model predict THAT? (Part 2 of 2)](https://towardsdatascience.com/why-did-your-model-predict-that-part-2-of-2-48e3d50e1daf)

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
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestAudit.pmml --target-class 0 --csv-input src/main/resources/csv/Audit.csv --csv-output Audit-impact.csv
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestAudit.pmml --target-class 1 --aggregate true --csv-input src/main/resources/csv/Audit.csv --csv-output Audit-aggregate_impact.csv
```

Scoring the regression example:

```
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --pmml-model src/main/resources/pmml/RandomForestAuto.pmml --csv-input src/main/resources/csv/Auto.csv --csv-output Auto-impact.csv
```

Getting help:

```
$ java -jar target/feature_impact-executable-1.0-SNAPSHOT.jar --help
```