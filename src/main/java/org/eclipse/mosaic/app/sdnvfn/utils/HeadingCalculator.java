package org.eclipse.mosaic.app.sdnvfn.utils;

public class HeadingCalculator {

    // Converte graus para radianos
    private static double toRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }

    // Converte radianos para graus
    private static double toDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }

    // Calcula o heading desejado em direção ao ponto alvo
    private static double calculateTargetHeading(double lat1, double lon1, double lat2, double lon2) {
        double dLon = toRadians(lon2 - lon1);
        double lat1Rad = toRadians(lat1);
        double lat2Rad = toRadians(lat2);

        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);

        double theta = Math.atan2(y, x);

        // Converte o heading de radianos para graus e ajusta para o intervalo [0, 360)
        double targetHeading = (toDegrees(theta) + 360) % 360;

        return targetHeading;
    }

    // Calcula a diferença entre o heading real e o heading desejado
    public static double calculateHeadingDifference(double realHeading, double lat1, double lon1, double lat2, double lon2) {
        double targetHeading = calculateTargetHeading(lat1, lon1, lat2, lon2);

        // Calcula a diferença entre o heading real e o heading desejado
        double headingDifference = realHeading - targetHeading;

        // Ajusta a diferença para o intervalo [-180, 180]
        headingDifference = Math.abs((headingDifference + 180) % 360 - 180);

        return headingDifference;
    }
}