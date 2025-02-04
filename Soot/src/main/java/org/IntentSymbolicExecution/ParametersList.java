package org.IntentSymbolicExecution;

import soot.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a collection of Parameter objects.
 * It provides methods to add new parameters and retrieve them by their register name.
 */
public class ParametersList {

    /**
     * List to store Parameter objects.
     */
    private final List<Parameter> parameters = new ArrayList<Parameter>();

    /**
     * Default constructor for ParametersList.
     */
    public ParametersList() {
    }

    /**
     * Adds a new Parameter to the list.
     *
     * @param registerName The register name associated with the parameter.
     * @param customName   The custom name associated with the parameter.
     * @param startUnit    The current node
     */
    public void addParameter(String registerName, String customName, Unit startUnit) {
        // check if the parameter already exist
        for (Parameter parameter : parameters) {
            if (parameter.getRegisterName().equals(registerName)) { // already tracked
                if (parameter.getFirstNode().equals(startUnit)) { // if it has same start, restart tracking
                    parameter.setStarted(true);
                    return;
                } else { // have a different start, i.e. reuse of register
                    if (parameter.isStarted() && !parameter.isFinished()) { // if started but not finished, stop tracking
                        parameter.setFinished(true);
                        parameter.setLastNode(startUnit);
                    }
                }
            }
        }
        parameters.add(new Parameter(registerName, customName, startUnit));
    }

    /**
     * Retrieves a Parameter from the list based on the register name.
     *
     * @param registerName The register name associated with the parameter.
     * @return A list of Parameters object.
     */
    public List<Parameter> getParameters(String registerName) {
        List<Parameter> result = new ArrayList<>();
        for (Parameter p : parameters)
            if (p.getRegisterName().equals(registerName))
                result.add(p);

        return result;
    }

//    /**
//     * Checks whether a parameter with the specified register name exists in the list of parameters.
//     *
//     * @param registerName The register name to check for in the list of parameters.
//     * @return {@code True} if the parameter exists in the list, {@code False} otherwise.
//     */
//    public boolean contains(String registerName) {
//        return !getParameters(registerName).isEmpty();
//    }


    public int size() {
        return parameters.size();
    }

    public boolean containInLine(Unit unit) {
        for (Parameter p : parameters)
            if (unit.toString().contains(p.getRegisterName())) {
                if (p.getFirstNode().equals(unit)) {
                    p.setStarted(true);
                    return true;
                } else if (p.isStarted() && !p.isFinished())
                    return true;
            }

        return false;
    }

//    public void updateFinish(String newParameterName, Unit unit) {
//        getParameters(newParameterName).setLastNode(unit);
//    }

    public void resetStartAndFinish() {
        for (Parameter p : parameters)
            p.resetStartAndFinish();
    }

    /**
     * This class represents a single parameter with a register name and custom name.
     * It also contains fields related to the execution state of the parameter.
     */
    public class Parameter {
        private final String registerName;
        private final String customName;

        // Fields for execution state tracking
        private Unit firstNode;
        private Unit lastNode;

        private boolean started = false;
        private boolean finished = false;

        /**
         * Constructor to initialize a Parameter object with a register and custom name.
         *
         * @param registerName The register name of the parameter.
         * @param customName   The custom name of the parameter.
         */
        public Parameter(String registerName, String customName) {
            this.registerName = registerName;
            this.customName = customName;
        }

        public Parameter(String registerName, String customName, Unit startUnit) {
            this.registerName = registerName;
            this.customName = customName;
            firstNode = startUnit;
            started = true;
        }

        /**
         * Retrieves the register name of the parameter.
         *
         * @return The register name of the parameter.
         */
        public String getRegisterName() {
            return registerName;
        }

        /**
         * Retrieves the custom name of the parameter.
         *
         * @return The custom name of the parameter.
         */
        public String getCustomName() {
            return customName;
        }

        /**
         * Retrieves the start execution unit.
         *
         * @return The start unit of type soot.Unit.
         */
        public Unit getFirstNode() {
            return firstNode;
        }

        /**
         * Sets the start execution unit.
         *
         * @param firstNode The start unit of type soot.Unit.
         */
        private void setFirstNode(Unit firstNode) {
            this.firstNode = firstNode;
        }

        /**
         * Retrieves the finish execution unit.
         *
         * @return The finish unit of type soot.Unit.
         */
        public Unit getLastNode() {
            return lastNode;
        }

        /**
         * Sets the finish execution unit.
         *
         * @param lastNode The finish unit of type soot.Unit.
         */
        private void setLastNode(Unit lastNode) {
            this.lastNode = lastNode;
            finished = true;
        }

        /**
         * Checks if the parameter has started.
         *
         * @return True if started, false otherwise.
         */
        public boolean isStarted() {
            return started;
        }

        /**
         * Sets the started state for the parameter.
         *
         * @param started The state to set for started (true/false).
         */
        private void setStarted(boolean started) {
            this.started = started;
        }

        /**
         * Checks if the parameter has finished.
         *
         * @return True if finished, false otherwise.
         */
        public boolean isFinished() {
            return finished;
        }

        /**
         * Sets the finished state for the parameter.
         *
         * @param finished The state to set for finished (true/false).
         */
        private void setFinished(boolean finished) {
            this.finished = finished;
        }

        private void resetStartAndFinish() {
            started = false;
            finished = false;
        }
    }
}


