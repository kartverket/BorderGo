/**
 * This package contains everything needed to maintain a transformation between the Tango
 * coordinate system and a geographical coordinate system.
 *
 * The main access point to this package is the abstract class
 * {@link no.kartverket.positionorientation.PositionOrientationProvider PositionOrientationProvider}
 * with its specific realization:
 * {@link no.kartverket.positionorientation.TangoPositionOrientationProvider TangoPositionOrientationProvider}.
 *
 *
 *
 * The package requires the <a href="http://ejml.org/">Efficient java matrix Library (ejml)</a>,
 * currently of version 0.31.
 *
 * @author runaas
 */
package no.kartverket.positionorientation;
