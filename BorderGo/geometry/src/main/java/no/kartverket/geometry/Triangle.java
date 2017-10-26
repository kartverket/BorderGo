package no.kartverket.geometry;

/**
 * Class and method for finding vector of shortest distance between a point and a triangle
 * <p>
 * Translated from this <A HREF="https://se.mathworks.com/matlabcentral/fileexchange/22857-distance-between-a-point-and-a-triangle-in-3d?requestedDomain=www.mathworks.com">Matlab code</A>
 * subject to the following copyright:
 * <p>
 * Copyright (c) 2009, Gwendolyn Fischer
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p>
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.<br>
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the distribution
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * Original Matlab code is also added as a comment to the end of the source code file
 */

public class Triangle {

    /**
     * Compute scalar product between 3 dimensional vectors
     * @param a a 3 dimensional vector
     * @param b another 3 dimensional vecor
     * @return scalar product between a and b
     */
    static public float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * Compute distance and direction between point and triangle. The parameter {@code res} will be filled
     * with the plane equation for the closest plane facing the point {@code p}, where the three first
     * elements are the normalized surface normal vector coordinates and the forth is the plane constant.
     * The point {@code x, y, z} is in the plane if the coordinates satisfies the equation
     * {@code x*res[0] + y*res[1] + z*res[2] = res[3]}. If the closest point
     * is inside the triangle the plane is identical to the triangle plane. If the closest point is
     * on an edge or corner the plane is normal to the vector between {@code p} and the closest point.
     *
     * @param p1 first corner of triangle
     * @param p2 second corner of triangle
     * @param p3 third corner of triangle
     * @param p a point
     * @param res equation of the closest plane to the point p, a {@code float[4]}
     * @return distance between point and triangle
     */
    static public float pointTriangle(float[] p1, float[] p2, float[] p3, float[] p, float[] res) {
        // rewrite triangle in normal form
        float[] B = p1;
        float[] E0 = new float[]{p2[0] - B[0], p2[1] - B[1], p2[2] - B[2]};
        float[] E1 = new float[]{p3[0] - B[0], p3[1] - B[1], p3[2] - B[2]};
        float[] D = new float[]{B[0] - p[0], B[1] - p[1], B[2] - p[2]};

        float a = dot(E0, E0);
        float b = dot(E0, E1);
        float c = dot(E1, E1);
        float d = dot(E0, D);
        float e = dot(E1, D);
        float f = dot(D, D);

        float det = a * c - b * b; // do we have to use abs here?
        float s = b * e - c * d;
        float t = b * d - a * e;


        // Tetrible tree of conditionals to determine in which region of the diagram
        // shown above the projection of the point into the triangle-plane lies.
        float sqrDistance = Float.MAX_VALUE;
        if ((s + t) <= det) {
            if (s < 0) {
                if (t < 0) {
                    // region4
                    if (d < 0) {
                        t = 0;
                        if (-d >= a) {
                            s = 1;
                            sqrDistance = a + 2 * d + f;
                        } else {
                            s = -d / a;
                            sqrDistance = d * s + f;
                        }
                    } else {
                        s = 0;
                        if (e >= 0) {
                            t = 0;
                            sqrDistance = f;
                        } else {
                            if (-e >= c) {
                                t = 1;
                                sqrDistance = c + 2 * e + f;
                            } else {
                                t = -e / c;
                                sqrDistance = e * t + f;
                            }
                        }
                    }
                } // of region 4
                else {
                    // region 3
                    s = 0;
                    if (e >= 0) {
                        t = 0;
                        sqrDistance = f;
                    } else {
                        if (-e >= c) {
                            t = 1;
                            sqrDistance = c + 2 * e + f;
                        } else {
                            t = -e / c;
                            sqrDistance = e * t + f;
                        }
                    }
                } // of region 3
            } else {
                if (t < 0) {
                    // region 5
                    t = 0;
                    if (d >= 0) {
                        s = 0;
                        sqrDistance = f;
                    } else {
                        if (-d >= a) {
                            s = 1;
                            sqrDistance = a + 2 * d + f;// GF 20101013 fixed typo d*s ->2*d
                        } else {
                            s = -d / a;
                            sqrDistance = d * s + f;
                        }
                    }
                } else {
                    // region 0
                    float invDet = 1 / det;
                    s = s * invDet;
                    t = t * invDet;
                    sqrDistance = s * (a * s + b * t + 2 * d) + t * (b * s + c * t + 2 * e) + f;
                }
            }
        } else {
            if (s < 0) {
                // region 2
                float tmp0 = b + d;
                float tmp1 = c + e;
                if (tmp1 > tmp0) { // minimum on edge s+t=1
                    float numer = tmp1 - tmp0;
                    float denom = a - 2 * b + c;
                    if (numer >= denom) {
                        s = 1;
                        t = 0;
                        sqrDistance = a + 2 * d + f; // GF 20101014 fixed typo 2*b -> 2*d
                    } else {
                        s = numer / denom;
                        t = 1 - s;
                        sqrDistance = s * (a * s + b * t + 2 * d) + t * (b * s + c * t + 2 * e) + f;
                    }
                } else {    // minimum on edge s=0
                    s = 0;
                    if (tmp1 <= 0) {
                        t = 1;
                        sqrDistance = c + 2 * e + f;
                    } else {
                        if (e >= 0) {
                            t = 0;
                            sqrDistance = f;
                        } else {
                            t = -e / c;
                            sqrDistance = e * t + f;
                        }
                    }
                }
            } // of region 2
            else {
                if (t < 0) {
                    // region6
                    float tmp0 = b + e;
                    float tmp1 = a + d;
                    if (tmp1 > tmp0) {
                        float numer = tmp1 - tmp0;
                        float denom = a - 2 * b + c;
                        if (numer >= denom) {
                            t = 1;
                            s = 0;
                            sqrDistance = c + 2 * e + f;
                        } else {
                            t = numer / denom;
                            s = 1 - t;
                            sqrDistance = s * (a * s + b * t + 2 * d) + t * (b * s + c * t + 2 * e) + f;
                        }
                    } else {
                        t = 0;
                        if (tmp1 <= 0) {
                            s = 1;
                            sqrDistance = a + 2 * d + f;
                        } else {
                            if (d >= 0) {
                                s = 0;
                                sqrDistance = f;
                            } else {
                                s = -d / a;
                                sqrDistance = d * s + f;
                            }
                        }
                    }
                } //end region 6
                else {
                    // region 1
                    float numer = c + e - b - d;
                    if (numer <= 0) {
                        s = 0;
                        t = 1;
                        sqrDistance = c + 2 * e + f;
                    } else {
                        float denom = a - 2 * b + c;
                        if (numer >= denom) {
                            s = 1;
                            t = 0;
                            sqrDistance = a + 2 * d + f;
                        } else {
                            s = numer / denom;
                            t = 1 - s;
                            sqrDistance = s * (a * s + b * t + 2 * d) + t * (b * s + c * t + 2 * e) + f;
                        }
                    }
                } //of region 1
            }
        }

        if (res == null)
            return sqrDistance <= 0 ? 0 : (float) Math.sqrt(sqrDistance);

        d = B[0] + s * E0[0] + t * E1[0];
        e = B[1] + s * E0[1] + t * E1[1];
        f = B[2] + s * E0[2] + t * E1[2];
        a = p[0] - d;
        b = p[1] - e;
        c = p[2] - f;

        float invLen, len = 0;
        if (sqrDistance < 1e-4) {
            a = E0[1] * E1[2] - E0[2] * E1[1];
            b = E0[2] * E1[0] - E0[0] * E1[2];
            c = E0[0] * E1[1] - E0[1] * E1[0];
            invLen = 1 / (float) Math.sqrt(a * a + b * b + c * c);
        } else {
            len = (float) Math.sqrt(a * a + b * b + c * c);
            invLen = 1 / len;
        }

        a *= invLen;
        b *= invLen;
        c *= invLen;
        res[0] = a;
        res[1] = b;
        res[2] = c;
        res[3] = a * d + b * e + c * f;
        return len;
    }
}
/*



function [dist,PP0] = pointTriangleDistance(TRI,P)
% calculate distance between a point and a triangle in 3D
            % SYNTAX
%   dist = pointTriangleDistance(TRI,P)
%   [dist,PP0] = pointTriangleDistance(TRI,P)
%
% DESCRIPTION
%   Calculate the distance of a given point P from a triangle TRI.
%   Point P is a row vector of the form 1x3. The triangle is a matrix
%   formed by three rows of points TRI = [P1;P2;P3] each of size 1x3.
%   dist = pointTriangleDistance(TRI,P) returns the distance of the point P
%   to the triangle TRI.
%   [dist,PP0] = pointTriangleDistance(TRI,P) additionally returns the
%   closest point PP0 to P on the triangle TRI.
%
        % Author: Gwendolyn Fischer
% Release: 1.0
            % Release date: 09/02/02
            % Release: 1.1 Fixed Bug because of normalization
% Release: 1.2 Fixed Bug because of typo in region 5 20101013
            % Release: 1.3 Fixed Bug because of typo in region 2 20101014

            % Possible extention could be a version tailored not to return the distance
% and additionally the closest point, but instead return only the closest
% point. Could lead to a small speed gain.

% Example:
            % %% The Problem
% P0 = [0.5 -0.3 0.5];
%
% P1 = [0 -1 0];
% P2 = [1  0 0];
% P3 = [0  0 0];
%
% vertices = [P1; P2; P3];
% faces = [1 2 3];

% %% The Engine
% [dist,PP0] = pointTriangleDistance([P1;P2;P3],P0);
%
% %% Visualization
% [x,y,z] = sphere(20);
% x = dist*x+P0(1);
% y = dist*y+P0(2);
% z = dist*z+P0(3);
%
% figure
% hold all
% patch('Vertices',vertices,'Faces',faces,'FaceColor','r','FaceAlpha',0.8);
% plot3(P0(1),P0(2),P0(3),'b*');
% plot3(PP0(1),PP0(2),PP0(3),'*g')
% surf(x,y,z,'FaceColor','b','FaceAlpha',0.3)
% view(3)

% The algorithm is based on
% "David Eberly, 'Distance Between Point and Triangle in 3D',
% Geometric Tools, LLC, (1999)"
% http:\\www.geometrictools.com/Documentation/DistancePoint3Triangle3.pdf
%
%        ^t
%  \     |
%   \reg2|
%    \   |
%     \  |
%      \ |
%       \|
%        *P2
%        |\
%        | \
%  reg3  |  \ reg1
%        |   \
%        |reg0\
%        |     \
%        |      \ P1
% -------*-------*------->s
%        |P0      \
%  reg4  | reg5    \ reg6


%% Do some error checking
if nargin<2
    error('pointTriangleDistance: too few arguments see help.');
    end
            P = P(:)';
            if size(P,2)~=3
    error('pointTriangleDistance: P needs to be of length 3.');
    end

if size(TRI)~=[3 3]
    error('pointTriangleDistance: TRI needs to be of size 3x3.');
    end

% ToDo: check for colinearity and/or too small triangles.


            % rewrite triangle in normal form
            B = TRI(1,:);
    E0 = TRI(2,:)-B;
%E0 = E0/sqrt(sum(E0.^2)); %normalize vector
    E1 = TRI(3,:)-B;
%E1 = E1/sqrt(sum(E1.^2)); %normalize vector


    D = B - P;
    a = dot(E0,E0);
    b = dot(E0,E1);
    c = dot(E1,E1);
    d = dot(E0,D);
    e = dot(E1,D);
    f = dot(D,D);

    det = a*c - b*b; % do we have to use abs here?
    s   = b*e - c*d;
    t   = b*d - a*e;

% Terible tree of conditionals to determine in which region of the diagram
% shown above the projection of the point into the triangle-plane lies.
            if (s+t) <= det
  if s < 0
            if t < 0
            %region4
      if (d < 0)
    t = 0;
        if (-d >= a)
    s = 1;
    sqrDistance = a + 2*d + f;
        else
    s = -d/a;
    sqrDistance = d*s + f;
    end
      else
    s = 0;
        if (e >= 0)
    t = 0;
    sqrDistance = f;
        else
                if (-e >= c)
    t = 1;
    sqrDistance = c + 2*e + f;
          else
    t = -e/c;
    sqrDistance = e*t + f;
    end
            end
    end %of region 4
            else
            % region 3
    s = 0;
      if e >= 0
    t = 0;
    sqrDistance = f;
      else
              if -e >= c
            t = 1;
    sqrDistance = c + 2*e +f;
        else
    t = -e/c;
    sqrDistance = e*t + f;
    end
            end
    end %of region 3
            else
            if t < 0
            % region 5
    t = 0;
      if d >= 0
    s = 0;
    sqrDistance = f;
      else
              if -d >= a
            s = 1;
    sqrDistance = a + 2*d + f;% GF 20101013 fixed typo d*s ->2*d
        else
    s = -d/a;
    sqrDistance = d*s + f;
    end
            end
    else
            % region 0
    invDet = 1/det;
    s = s*invDet;
    t = t*invDet;
    sqrDistance = s*(a*s + b*t + 2*d) ...
            + t*(b*s + c*t + 2*e) + f;
    end
            end
else
        if s < 0
            % region 2
    tmp0 = b + d;
    tmp1 = c + e;
    if tmp1 > tmp0 % minimum on edge s+t=1
    numer = tmp1 - tmp0;
    denom = a - 2*b + c;
      if numer >= denom
            s = 1;
    t = 0;
    sqrDistance = a + 2*d + f; % GF 20101014 fixed typo 2*b -> 2*d
      else
    s = numer/denom;
    t = 1-s;
    sqrDistance = s*(a*s + b*t + 2*d) ...
            + t*(b*s + c*t + 2*e) + f;
    end
    else          % minimum on edge s=0
    s = 0;
      if tmp1 <= 0
    t = 1;
    sqrDistance = c + 2*e + f;
      else
              if e >= 0
    t = 0;
    sqrDistance = f;
        else
    t = -e/c;
    sqrDistance = e*t + f;
    end
            end
    end %of region 2
            else
            if t < 0
            %region6
            tmp0 = b + e;
    tmp1 = a + d;
      if (tmp1 > tmp0)
    numer = tmp1 - tmp0;
    denom = a-2*b+c;
        if (numer >= denom)
    t = 1;
    s = 0;
    sqrDistance = c + 2*e + f;
        else
    t = numer/denom;
    s = 1 - t;
    sqrDistance = s*(a*s + b*t + 2*d) ...
            + t*(b*s + c*t + 2*e) + f;
    end
      else
    t = 0;
        if (tmp1 <= 0)
    s = 1;
    sqrDistance = a + 2*d + f;
        else
                if (d >= 0)
    s = 0;
    sqrDistance = f;
          else
    s = -d/a;
    sqrDistance = d*s + f;
    end
            end
    end
      %end region 6
            else
            % region 1
    numer = c + e - b - d;
      if numer <= 0
    s = 0;
    t = 1;
    sqrDistance = c + 2*e + f;
      else
    denom = a - 2*b + c;
        if numer >= denom
            s = 1;
    t = 0;
    sqrDistance = a + 2*d + f;
        else
    s = numer/denom;
    t = 1-s;
    sqrDistance = s*(a*s + b*t + 2*d) ...
            + t*(b*s + c*t + 2*e) + f;
    end
    end %of region 1
    end
            end
    end

% account for numerical round-off error
if (sqrDistance < 0)
    sqrDistance = 0;
    end

            dist = sqrt(sqrDistance);

if nargout>1
    PP0 = B + s*E0 + t*E1;
    end
*/

