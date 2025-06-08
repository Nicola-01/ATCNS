# Intent Symbolic Execution

Intent Symbolic Execution is a semi-automated analysis pipeline for Android applications that statically extracts control-flow paths influenced by `Intent` extras, symbolically solves path constraints with an SMT solver, and dynamically validates the generated Intents in an emulated environment.  
This project was developed as part of the **Advanced Topics in Computer and Network Security** course in the Master's Degree in Cybersecurity at the **University of Padua**.

## Presentation

[project presentation](https://www.canva.com/design/DAGpMDtDMY0/3JR3Iip4Q4d2L6ByZu67rw/edit?utm_content=DAGpMDtDMY0&utm_campaign=designshare&utm_medium=link2)

## Prerequisites

To run the tool, make sure you have the following components installed:

### Core Requirements

- **Java 21+** – Required to run the static analysis tool.
- **Maven** – Used to build the Java project and manage dependencies.
- **Python 3.13+** – Used for symbolic execution and dynamic analysis.
- **Z3 SMT Solver** – Install via `pip install z3-solver`.

### System Dependencies

Install these via `apt`:

```bash
sudo apt install adb apktool graphviz graphviz-dev
```

### Python Dependencies

Install these via `pip`:

```bash
pip install z3-solver androguard openai
```

### Android SDK Tools

Install the Android command-line tools (assumes Android SDK is set up):

```bash
sdkmanager "emulator"
```

You may be prompted to accept licenses by pressing `y`.

> Note: When running `send_intents.py` or launching the emulator programmatically, the license agreement prompt **may appear silently** — that is, **no visible "Accept (y/n)" message is shown in the terminal**, but the process will be waiting for input.  
You must still press `y` and then `Enter` to proceed, even if nothing is visibly printed.

Required components:

- `avdmanager` & `sdkmanager` (part of Android SDK tools)
- Android Emulator system images (auto-downloaded on first use)

## Build Instructions

This project is built using _Maven_, a Java project management and build tool. Make sure you have Maven installed (it is separate from the JDK).

To compile the static analysis tool and automatically download all dependencies, run:

```bash
mvn clean package
```
This will generate the executable JAR file at:
```bash
java -jar target/Soot-1.0-SNAPSHOT.jar
```

## Project Structure

The pipeline is composed of three main phases:

### 1. Static Analysis (Java + Soot)

- Parses APK manifest to detect exported components
- Builds Control Flow Graphs (CFGs) using Soot for each exported method
- Identifies execution paths that depend on `Intent` extras
- Exports valid paths in DOT format

**Usage**:
```bash
# Direct mode
java -jar target/Soot-1.0-SNAPSHOT.jar path/to/app.apk

# Interactive mode (scans `Applications_to_analise/` directory)
java -jar target/Soot-1.0-SNAPSHOT.jar
```

> In _Direct mode_, the user specifies an APK located anywhere in the filesystem. This mode is intended for quick, one-off analyses and supports arbitrary APK locations. <br>
> In _Interactive mode_, the system lists all APKs in the predefined Applications_to_analise/ directory. The user selects the target app from this list. This mode simplifies repeated testing and supports reproducible experiments by relying on a fixed input location.

### 2. Constraint Solving (Python + Z3)

- Parses DOT files and extracts constraints from `if` conditions
- Translates them into Z3 expressions
- Solves constraints to find concrete values for `Intent` extras and actions

**Usage**:

```bash
python3 Z3Solver/z3_autosolver.py
```
> The script provides an interactive menu to select the APK directory containing the `.dot` files generated in Phase 1, similar to the interactive mode used in the static analysis phase.

### 3. Dynamic Testing & Log Analysis (Python + ADB + LLM)

- Launches emulator matching app's target SDK
- Installs the APK and injects generated Intents
- Captures logs via `adb logcat`
- Uses an LLM to analyse logs and detect crashes or anomalies

**Usage**:
```bash
python3 IntentSender/send_intent.py Soot/Applications_to_analise/<app.apk> Z3Solver/analysis_results/<app.apk>
```
> Positional arguments: <br>
>   `apk_path`: Path to the APK file (installed on the emulator)<br>
>   `analysis_path`: Directory containing `.txt` output files from Z3 (`Z3Solver/analysis_results/<apkName>/`)

**Authentication**:

Before running the script, create a file named `api.key` in the `IntentSender` directory. This file must contain a valid API key for the Groq API (OpenAI-compatible endpoint).

**Customisation**:

You can modify the target LLM and endpoint directly in the script by changing:

```python
client = OpenAI(
    base_url="https://api.groq.com/openai/v1",
    api_key=api_key,
)
model = "llama-3.3-70b-versatile"
```

This allows switching between different models or endpoints depending on the provider or deployment scenario.

## Repository Layout

```
├── Soot/                                                           # Java static analyzer
│ ├── Applications_to_analise/                                      # APKs to analyse
│ └── paths/<apkName>/                                              # Full CFGs and execution paths exported as .dot
├── Z3Solver/                                                       # Python symbolic executor
│ └── analysis_results/<apkName>/<appMethod>_analysis_results.txt   # Z3 constraint solving output 
├── IntentSender/                                                   # Python dynamic tester
│ └── intent_results<apkName>.json                                  # JSON logs of sent intents and outcomes
├── dot/                                                            # Examples of .dot files
├── Apps/                                                           # Synthetic test apps used during development
└── README.md
```
  
## Authors

| **[Nicola Busato](https://github.com/Nicola-01)**| **[Jacopo Momesso](https://github.com/JapoMomi1)** |
| :---: | :---: |
| <a href="https://github.com/Nicola-01"><img src="https://avatars1.githubusercontent.com/u/96294696?s=100&v=4" alt="Nicola Busato" width="150"/></a> | <a href="https://github.com/JapoMomi1"><img src="https://avatars1.githubusercontent.com/u/127385689?s=100&v=4" alt="Jacopo Momesso" width="150"/></a>|
