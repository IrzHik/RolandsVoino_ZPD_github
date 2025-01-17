import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class atbildes_aprekinasana {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            try {
                System.out.print("\n\nIevadiet attela nosaukumu: ");
                String num1 = scanner.nextLine();
                System.out.print("Ievadiet GPA numuru: ");
                String num2 = scanner.nextLine();

                File folder = new File("analizes_vesture");
                File[] files = folder.listFiles((dir, name) -> 
                    name.startsWith("analize_" + num1 + "_" + num2 + "_") && name.endsWith(".txt"));

                if (files == null || files.length == 0) {
                    System.out.println("\nFails netika atrasts.");
                    for(int i=0; i<40; i++){
                        System.out.print("===");
                    }
                    continue;
                }

                int fileChoice = 0;
                if (files.length > 1) {
                    System.out.println("\nPiedavati faili:");
                    for (int i = 0; i < files.length; i++) {
                        System.out.println((i + 1) + ". " + files[i].getName());
                    }

                    System.out.print("\nIzvelieties faila numuru: ");
                    fileChoice = Integer.parseInt(scanner.nextLine()) - 1;
                    
                    if (fileChoice < 0 || fileChoice >= files.length) {
                        System.out.println("\nTadas izveles nav.");
                        for(int i=0; i<40; i++){
                            System.out.print("===");
                        }
                        continue;
                    }
                }

                String filePath = files[fileChoice].getPath();
                double[] values = new double[7];
                int valueIndex = 0;

                try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                    String line;
                    int lineNumber = 0;
                    while ((line = br.readLine()) != null) {
                        lineNumber++;
                        if (lineNumber == 8 || lineNumber == 26 || lineNumber == 29 || 
                            lineNumber == 30 || lineNumber == 35 || lineNumber == 36 || 
                            lineNumber == 38) {
                            String numbers = line.replaceAll("[^0-9.]", "").replaceAll("\\.$", "");
                            if (!numbers.isEmpty()) {
                                values[valueIndex++] = Double.parseDouble(numbers);
                            }
                        }
                    }

                    int uzticiba = (int)values[0];
                    double baltoPikseluRelativaisDaudzums = values[1];
                    int caurumuSkaits = (int)values[2];
                    double caurumuPlatiba = values[3];
                    double pilniguBaltuStripuIpatsvars = values[4];
                    int krasuGrupuSkaits = (int)values[5];
                    double relativaisKontrasts = values[6];

                    System.out.println();
                    for(int i=0; i<40; i++){
                        System.out.print("===");
                    }


                    int punktiUzticiba = (int)(uzticiba * 3);
                    if(punktiUzticiba > 100){
                        punktiUzticiba = 100;
                    }else{}

                    int punktiBaltuPikseluRelativaisDaudzums = (int)(baltoPikseluRelativaisDaudzums * 10 - 450);
                    if(baltoPikseluRelativaisDaudzums > 65){
                        punktiBaltuPikseluRelativaisDaudzums = 0;
                    }else if(punktiBaltuPikseluRelativaisDaudzums < 0){
                        punktiBaltuPikseluRelativaisDaudzums = 0;
                    }else if(punktiBaltuPikseluRelativaisDaudzums > 150){
                        punktiBaltuPikseluRelativaisDaudzums = 150;
                    }else{}

                    int punktiPilniguBaltuStripuIpatsvars = (int)(pilniguBaltuStripuIpatsvars * 2 - 100);
                    if(punktiPilniguBaltuStripuIpatsvars < 0){
                        punktiPilniguBaltuStripuIpatsvars = 0;
                    }else if(punktiPilniguBaltuStripuIpatsvars > 100){
                        punktiPilniguBaltuStripuIpatsvars = 100;
                    }else{}

                    int punktiRelativaisKontrasts = (int)((100*relativaisKontrasts - 4700)/9);
                    if(punktiRelativaisKontrasts < 0){
                        punktiRelativaisKontrasts = 0;
                    }else if(punktiRelativaisKontrasts > 200){
                        punktiRelativaisKontrasts = 200;
                    }else{}

                    int punktiKrasuGrupuSkaits = 0;
                    if(krasuGrupuSkaits >= 0 && krasuGrupuSkaits < 3){
                        punktiKrasuGrupuSkaits = 150;
                    }else{}

                    int punktiCaurumuSkaits = 0;
                    if(caurumuSkaits == 0){
                        punktiCaurumuSkaits = 200;
                    }else if(caurumuSkaits > 0 && caurumuSkaits < 3){
                        punktiCaurumuSkaits = 100;
                    }else if(caurumuSkaits >= 3){
                        punktiCaurumuSkaits = 0;
                    }else{}

                    int punktiCaurumuPlatiba = (int)(200 - caurumuPlatiba*40);
                    if(punktiCaurumuPlatiba < 0){
                        punktiCaurumuPlatiba = 0;
                    }else{}

                    int kopejiePunkti = punktiUzticiba + punktiBaltuPikseluRelativaisDaudzums + punktiPilniguBaltuStripuIpatsvars + punktiRelativaisKontrasts + punktiKrasuGrupuSkaits + punktiCaurumuSkaits + punktiCaurumuPlatiba;

                    System.out.println("\n\n analize_" + num1 + "_" + num2 + "\n\nPunkti:\n");
                    System.out.println("Uzticiba: " + punktiUzticiba + "p / 100p");
                    System.out.println("Baltu pikselu relativais daudzums: " + punktiBaltuPikseluRelativaisDaudzums + "p / 150p");
                    System.out.println("Pilnigi baltu stripu ipatsvars: " + punktiPilniguBaltuStripuIpatsvars + "p / 100p");
                    System.out.println("Relativais kontrasts: " + punktiRelativaisKontrasts + "p / 200p");
                    System.out.println("Krasu grupu skaits: " + punktiKrasuGrupuSkaits + "p / 150p");
                    System.out.println("Caurumu skaits: " + punktiCaurumuSkaits + "p / 200p");
                    System.out.println("Caurumu platiba: " + punktiCaurumuPlatiba + "p / 200p");
                    System.out.println("\nKopejais punktu skaits: " + kopejiePunkti + "p / 1100p");


                    int atbilstibasApaksejaisSlieksnis = 915;
                    int neatbilstibasAugsejaisSlieksnis = 710;

                    int uzticibasSlieksnis = 10;
                    int baltuPikseļuAugsejaisSlieksnis = 151;
                    int baltuPikseļuApaksejaisSlieksnis = 103;
                    int kopejasNecaurredzamibasSlieksnis = 35;
                    int aseviskasNecaurredzamibasSlieksnis = 150;
                    int caurumuSkaitaAugsejaisSlieksnis = 200;
                    int caurumuSkaitaApaksejaisSlieksnis = 100;
                    int caurumuPlatiabasAugsejaisSlieksnis = 180;
                    int caurumuPlatiabasApaksejaisSlieksnis = 100;

                    boolean defekti = false;

                    System.out.println("\nIespejamie defekti:");

                    if(uzticibasSlieksnis <= punktiUzticiba){
                    }else{
                        System.out.println("Apskatamais objekts nav GPA");
                        defekti = true;
                    }

                    if(baltuPikseļuAugsejaisSlieksnis <= (punktiBaltuPikseluRelativaisDaudzums+punktiPilniguBaltuStripuIpatsvars)){
                    }else if(baltuPikseļuApaksejaisSlieksnis <= (punktiBaltuPikseluRelativaisDaudzums+punktiPilniguBaltuStripuIpatsvars)){
                        System.out.println("Baltu stripu garuma kluda ir virs 5%");
                        defekti = true;
                    }else{
                        System.out.println("Baltu stripu garuma kluda ir virs 20%");
                        defekti = true;
                    }

                    if(kopejasNecaurredzamibasSlieksnis <= punktiRelativaisKontrasts){
                    }else{
                        System.out.println("Nodilusi visa apzimejuma krasa");
                        defekti = true;
                    }

                    if(aseviskasNecaurredzamibasSlieksnis <= punktiKrasuGrupuSkaits){
                    }else{
                        System.out.println("Nodilusi atsevisko apzimejuma stripu krasa");
                        defekti = true;
                    }

                    if(caurumuSkaitaAugsejaisSlieksnis <= punktiCaurumuSkaits){
                    }else if(caurumuSkaitaApaksejaisSlieksnis <= punktiCaurumuSkaits){
                        System.out.println("Cauruma esamiba");
                        defekti = true;
                    }else{
                        System.out.println("Vairaku caurumu esamiba");
                        defekti = true;
                    }

                    if(caurumuPlatiabasAugsejaisSlieksnis <= punktiCaurumuPlatiba){
                    }else if(caurumuPlatiabasApaksejaisSlieksnis <= punktiCaurumuPlatiba){
                        System.out.println("Cauruma/caurumu aiznemta platiba ir virs 4%");
                        defekti = true;
                    }else{
                        System.out.println("Cauruma/caurumu aiznemta platiba ir virs 15%");
                        defekti = true;
                    }

                    if(!defekti){
                        System.out.println("Netika atrasti\n");
                    }


                    if(kopejiePunkti >= atbilstibasApaksejaisSlieksnis){
                        System.out.println("\nGPA atbilst standartiem.");
                    }else if(kopejiePunkti >= neatbilstibasAugsejaisSlieksnis){
                        System.out.println("\nGPA gandrizs atbilst standartiem.");
                    }else{
                        System.out.println("\nGPA neatbilst standartiem.");
                    }

                }

            } catch (IOException e) {
                System.out.println("Faila lasisanas kluda: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Kluda: " + e.getMessage());
            }
            System.out.println();
            for(int i=0; i<40; i++){
                System.out.print("===");
            }
        }
    }
}