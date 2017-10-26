package no.kartverket.geodesy;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import java.nio.ByteBuffer;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Created by hg on 18.08.2017.
 */


public class Href_grid {

    // Last href file is HREF2016B_NN2000_EUREF89.bin.
    // Rename extension to "wav" to avoid zipping of asset
    static final String href_file = "HREF_NN2000_EUREF89.wav";

    private static double dDUMMY = -999.0;
    private static float NO_VAL = 9999.0f;
    private static int BLOCK_SIZE = 64;

    private double _dNMin=0, _dEMin=0, _dNMax=0, _dEMax=0;
    private double _ddN=0, _ddE=0;
    private int _nRows =0;
    private int _nCols =0;
    private int _nVals =0;
    private int _nOk=0;
    private float[] _afVals = null;


    @Override
    public String toString() {
        return super.toString();
    }


    /**
     * Parse header and data from byte buffer
     * @param data
     * @return
     */
    private boolean SetData( byte [] data )
    {
        if ( ReadHeader( data ) )
        {
            if ( ReadData( data ) )
            {
                _nOk = 1;
                return true;
            }
        }

        return false;
    }


    private int ReadInt( int index, byte [] data )
    {
        int i0 = data[index];
        int i1 = data[index+1];
        int i2 = data[index+2];
        int i3 = data[index+3];

        int n = i0 + 256*( i1 + 256*( i2 +( 256*i3 ) ) );	// Reverse

        return n;
    }


    private double ReadDouble( int index, byte [] data )
    {
        byte[] tmp = new byte[8];
        for ( int i=0 ; i<8 ; i++ )
            tmp[i] = data[index+7-i];	// Reverse

        ByteBuffer bb = ByteBuffer.wrap( tmp );

        double	d = bb.getDouble();

        return d;
    }


    private float ReadFloat( int index, byte [] data )
    {
        byte[] tmp = new byte[4];
        for ( int i=0 ; i<4 ; i++ )
            tmp[i] = data[index+3-i];	// Reverse

        ByteBuffer bb = ByteBuffer.wrap( tmp );

        float	f = bb.getFloat();

        return f;
    }


    /**
     * Parse header
     * @param data
     * @return
     */
    private boolean ReadHeader( byte [] data )
    {
        int Code = ReadInt( 0, data );
        if ( Code != 777 )
            return false;

        _dNMin = ReadDouble(  4, data );
        _dNMax = ReadDouble( 12, data );
        _dEMin = ReadDouble( 20, data );
        _dEMax = ReadDouble( 28, data );
        _ddN   = ReadDouble( 36, data );
        _ddE   = ReadDouble( 44, data );

        int nUTM  = ReadInt( 52, data );
        int nEll  = ReadInt( 56, data );
        int nZone = ReadInt( 60, data );

        return true;
    }


    private boolean SaveVal( int nN, int nE, float fVal )
    {
        if ( nN<0 || nN>= _nRows)
            return false;
        if ( nE<0 || nE>= _nCols)
            return false;

        //double	N = _dNMin + lN*_ddN;
        //double	E = _dEMin + lE*_ddE;

        int	nIndex = nN* _nCols + nE;
        _afVals[nIndex] = fVal;

        return true;
    }


    /**
     * Parse data from byte buffer and save to float array
     * @param data
     * @return
     */
    private boolean ReadData( byte [] data )
    {
        int pos = 64;

        _nRows = (int)(( ( _dNMax - _dNMin ) / _ddN) + 0.5 ) + 1;
        _nCols = (int)(( ( _dEMax - _dEMin ) / _ddE) + 0.5 ) + 1;

        // Correct grid-spacing
        _ddN = ( _dNMax - _dNMin ) / ( _nRows - 1.0 );
        _ddE = ( _dEMax - _dEMin ) / ( _nCols - 1.0 );

        _nVals = _nRows * _nCols;
        _afVals = new float[_nVals];

        int nNum_Vals_Block = BLOCK_SIZE / 4;									// 64/4 = 16	= Vals/block
        int nNum_Blocks_Row = (int)Math.ceil( (double) _nCols / (double)nNum_Vals_Block );		// Blocks/Row, rounding up

        int	lNumNoVal = 0;
        int	lRealVals	= 0;

        for (int i = (_nRows -1); i>=0 ; i-- )				// All rows from north
        {
            //double	N = _dNMin + i*_ddN;

            for ( int j=0 ; j<nNum_Blocks_Row ; j++ )			// All blocks in one row
            {
                for ( int k=0 ; k<nNum_Vals_Block ; k++ )			// All vals in one block
                {
                    int	lEInd = j*nNum_Vals_Block + k;
                    if ( lEInd >= _nCols)
                    {
                        pos += ( 4*(16-k) );
                        break;	// End of row, jump over padding in this block.
                    }
                    float fVal = ReadFloat(pos, data);
                    pos += 4;
                    SaveVal( i, lEInd, fVal );

                    //double	E = _dEMin + lEInd*_ddE;

                    if ( fVal == NO_VAL )
                    {
                        lNumNoVal++;
                        continue;
                    }

                    lRealVals++;
                }
            }
        }

        return ( pos>0 );
    }


    private double N2Index( double N )
    {
        if ( _dNMin>N || _dNMax<N )
            return -1.0;

        double dNInd = ( N - _dNMin ) / _ddN;

        return dNInd;
    }
    private double E2Index( double E )
    {
        if ( _dEMin>E || _dEMax<E )
            return -1.0;

        double dEInd = ( E - _dEMin ) / _ddE;

        return dEInd;
    }


    private float GetTabVal( int nN, int nE )
    {
        if ( nN<0 || nN>= _nRows)
            return NO_VAL;
        if ( nE<0 || nE>= _nCols)
            return NO_VAL;

        int	nIndex = nN* _nCols + nE;
        float	f = _afVals[nIndex];
        return f;
    }


    private boolean IsDummy( float fLL, float fLR, float fUL, float fUR, double NFrac, double EFrac )
    {
        if ( fLL == NO_VAL )
            if ( ! (EFrac==1.0 || NFrac==1.0 ) )
                return true;

        if ( fLR == NO_VAL )
            if ( ! (EFrac==0.0 || NFrac==1.0 ) )
                return true;

        if ( fUL == NO_VAL )
            if ( ! (EFrac==1.0 || NFrac==0.0 ) )
                return true;

        if ( fUR == NO_VAL )
            if ( ! (EFrac==0.0 || NFrac==0.0 ) )
                return true;

        return false;	// No dummy
    }


    /**
     * Get one geoid height from position (N,E).
     * Read 4 values from float array and do bilinear interpolation.
     * @param N
     * @param E
     * @return Geoid height
     */
    public double GetVal( double N, double E )
    {
        if ( _nOk == 0 )
            return dDUMMY;

        double NInd = N2Index(N);
        double EInd = E2Index(E);

        int	nNInd = (int)(Math.floor( NInd ));
        int	nEInd = (int)(Math.floor( EInd ));

        double	EFrac = EInd - nEInd;
        double	NFrac = NInd - nNInd;

        float	fLL = GetTabVal( nNInd, nEInd );
        float	fLR = GetTabVal( nNInd, nEInd+1 );
        float	fUL = GetTabVal( nNInd+1, nEInd );
        float	fUR = GetTabVal( nNInd+1, nEInd+1 );

        if ( IsDummy( fLL, fLR, fUL, fUR, NFrac, EFrac ) )
            return dDUMMY;

        double	dLo = fLL + (fLR-fLL)*EFrac;
        double	dUp = fUL + (fUR-fUL)*EFrac;
        double	dLe = fLL + (fUL-fLL)*NFrac;
        double	dRi = fLR + (fUR-fLR)*NFrac;

        // Calc same result:
        //double	dVal1 = dLo + (dUp-dLo)*NFrac;
        //double	dVal2 = dLe + (dRi-dLe)*EFrac;
        double	dVal3 = fLL + (fUL-fLL)*NFrac + (fLR-fLL)*EFrac + (fLL-fLR-fUL+fUR)*EFrac*NFrac;

        return dVal3;
    }


    /**
     * Read href file to byte buffer and parse header and data
     * @param assetManager
     * @return
     */
    public boolean ReadHrefFile(AssetManager assetManager)
    {

        long file_len = 0;
        FileInputStream stream;

        try {
            AssetFileDescriptor descr = assetManager.openFd(href_file);
            file_len = descr.getLength();
            stream = descr.createInputStream();
        }
        catch(IOException ex){
            return false;
        }

        if ( file_len <= 0 )
            return false;

        byte[] data_href = new byte[(int)file_len];

        try {
            InputStream input = null;
            try {
                int totalBytesRead = 0;
                input = new BufferedInputStream(stream);
                while(totalBytesRead < data_href.length){
                    int bytesRemaining = data_href.length - totalBytesRead;
                    int bytesRead = input.read(data_href, totalBytesRead, bytesRemaining);
                    if (bytesRead > 0){
                        totalBytesRead = totalBytesRead + bytesRead;
                    }
                }
            }
            finally {
                input.close();
            }
        }
        catch (FileNotFoundException ex) {
            return false;
        }
        catch (IOException ex) {
            return false;
        }

        boolean bOk = SetData( data_href );

        return bOk;
    }

}
