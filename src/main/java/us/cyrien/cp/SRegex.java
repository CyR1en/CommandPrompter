package us.cyrien.cp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SRegex {

    public static final String ANSI_RESET = "\033[0m";
    public static final String ANSI_GREEN = "\033[42m";

    private Pattern regex;

    private final String sample =
            "Sample text for testing:\n" +
                    "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
                    "0123456789 _+-.,!@#$%^&*();\\/|<>\"'\n" +
                    "12345 -98.7 3.141 .6180 9,000 +42\n" +
                    "555.123.4567\t+1-(800)-555-2468\n" +
                    "\n" +
                    "[ABC] [abc] [Abc] [AbC]" +
                    "\n\n" + "<ABC> <abc> <Abc> <AbC>" + "\n\n" +
                    "foo@demo.net\tbar.ba@test.co.uk\n";

    private String result;
    private int numResults;
    private ArrayList<String> results;

    public SRegex() {
        result = "";
        numResults = 0;
        results = new ArrayList<>();
    }

    public void find(String sample,  String regex) {
        results = new ArrayList<>();
        String tempSample = sample;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(tempSample);
        this.regex = p;

        while (m.find()) {
            String result = m.group();
            results.add(result);
            tempSample = tempSample.substring(tempSample.indexOf(result) + result.length());
            m = p.matcher(tempSample);
        }

        if (results.size() < 1)
            numResults = 0;
        else {
            numResults = results.size();
        }
    }

    public void find( String regex) {
        find(getDefaultSample(), regex);
    }

    public void test(String sample, String regex) {
        String tempSample = sample;
        find(regex);
        for (String string : results) {
            tempSample = tempSample.replace(string, ANSI_GREEN + string + ANSI_RESET);
        }
        result = tempSample;
        System.out.println(this);
    }

    public void test( String regex) {
        test(getDefaultSample(), regex);
    }

    public List<String> getResults() {
        return results;
    }

    public Pattern getRegex() {
        return regex;
    }

    public String getDefaultSample() {
        return sample;
    }

    @Override
    public String toString() {
        String out = "Number of results: " + numResults + "\n\n" + "Results: " + getResults();
        out += "\n\n" + result;
        return out;
    }

}
