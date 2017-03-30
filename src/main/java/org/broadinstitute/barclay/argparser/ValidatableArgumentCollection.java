package org.broadinstitute.barclay.argparser;

/**
 * Marker class for {@link ArgumentCollection} containers that should be validated in the command line.
 *
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public interface ValidatableArgumentCollection {

    /**
     * Validate the class after command line parsing.
     *
     * @throws CommandLineException if the custom validation does not pass.
     */
    public void validateArguments() throws CommandLineException;

}
