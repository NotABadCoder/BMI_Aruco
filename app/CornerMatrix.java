import java.util.ArrayList;
import java.util.List;




public class CornerMatrix {
    public static void main(String[] args) {
        int size = 50; // Size of each square
        int distance = 50; // Distance between squares

        int numSquares = 10;
        int matrixWidth = size + (numSquares - 1) * distance;
        int matrixHeight = numSquares * size + (numSquares - 1) * distance;

        char[][] matrix = new char[matrixHeight][matrixWidth];

        for (int i = 0; i < matrixHeight; i++) {
            for (int j = 0; j < matrixWidth; j++) {
                matrix[i][j] = ' ';
            }
        }

        for (int i = 0; i < numSquares; i++) {
            int x = i * distance;
            int y = matrixHeight - (i + 1) * size - i * distance;

            for (int row = y; row < y + size; row++) {
                for (int col = x; col < x + size; col++) {
                    if (row >= 0 && row < matrixHeight && col >= 0 && col < matrixWidth) {
                        matrix[row][col] = '#';
                    }
                }
            }
        }

        // Print the matrix
        for (int i = 0; i < matrixHeight; i++) {
            for (int j = 0; j < matrixWidth; j++) {
                System.out.print(matrix[i][j]);
            }
            System.out.println();
        }
    }
}