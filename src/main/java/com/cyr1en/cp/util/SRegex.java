/*
 * MIT License
 *
 * Copyright (c) 2019 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.cp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SRegex {

    private final String DEFAULT_SAMPLE =
            "Sample text for testing:\n" +
                    "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ\n" +
                    "0123456789 _+-.,!@#$%^&*();\\/|<>\"'\n" +
                    "12345 -98.7 3.141 .6180 9,000 +42\n" +
                    "555.123.4567\t+1-(800)-555-2468\n" +
                    "\n" +
                    "[ABC] [abc] [Abc] [AbC]" +
                    "\n\n" + "<ABC> <abc> <Abc> <AbC>" + "\n\n" +
                    "foo@demo.net\tbar.ba@test.co.uk\n";

    public static final String ANSI_RESET = "\033[0m";
    public static final String ANSI_GREEN_FOREGROUND = "\033[42m";
    public static final String ANSI_GREEN = "\u001b[32m";

    private Pattern regex;

    private String sample;
    private String result;
    private int numResults;
    private List<String> resultsList;

    /**
     * Constructor that initializes {@see #regex} regex and automatically find matches
     * using {@see find(Pattern)} find(Pattern).
     *
     * @param sample sample to use for SRegex methods.
     * @param p Pattern to use as {@see #regex} regex.
     */
    public SRegex(String sample, Pattern p) {
        this.sample = sample == null ? DEFAULT_SAMPLE : sample ;
        result = this.sample;
        numResults = 0;
        resultsList = new ArrayList<>();
        regex = p;
        if(regex != null)
            find(p);
    }

    /**
     * Constructor that only initializes the sample.
     *
     * To initialize {@see #regex} regex. Use {@see #init(Patter)} init(Pattern),
     * {@see #find(Pattern)} find(Pattern), or {@see #test(Patter)} test(Pattern).
     *
     * @param sample sample to use for SRegex methods.
     */
    public SRegex(String sample) {
        this(sample, null);
    }

    /**
     * Constructor that uses {@see #DEFAULT_SAMPLE} DEFAULT_SAMPLE as {@see #sample} sample.
     *
     * To initialize {@see #regex} regex. Use {@see #init(Patter)} init(Pattern),
     * {@see #find(Pattern)} find(Pattern), or {@see #test(Patter)} test(Pattern).
     */
    public SRegex() {
        this(null, null);
    }

    /**
     * This is only used when find() {@see #find(Pattern)} or test() {@see #test(Pattern)} have not been used.
     *
     * @param p Pattern to initialize private field regex {@see #regex} instance with.
     * @return
     */
    public SRegex init(Pattern p) {
        regex = p;
        return this;
    }

    /**
     * This is only used when {@see #find(Pattern)} find() or {@see #test(Pattern)} test() have not been used.
     *
     * @param p Pattern as String to initialize private field {@see #regex} regex instance with.
     * @return current instance of SRegex.
     */
    public SRegex init(String p) {
        init(Pattern.compile(p));
        return this;
    }

    /**
     * Find substrings using regex. This will put all matched substrings to private instance of
     * {@link List} List<String> {@see #resultList} resultList.
     *
     * @param p Pattern to use for finding substrings. This will also initialize private field {@see #regex} regex.
     * @return current instance of SRegex.
     */
    public SRegex find(Pattern p) {
        regex = p;
        resultsList = new ArrayList<>();
        String tempSample = sample;
        Matcher m = p.matcher(tempSample);
        this.regex = p;

        while (m.find()) {
            String result = m.group();
            resultsList.add(result);
            tempSample = tempSample.substring(tempSample.indexOf(result) + result.length());
            m = p.matcher(tempSample);
        }

        if (resultsList.size() > 0)
            numResults = resultsList.size();
        return this;
    }

    /**
     * Test regex pattern, find substrings using that regex, change the color of the results, and print the sample.
     * This will put all matched substrings to private instance of {@link List} List<String>
     * {@see #resultList} resultList.
     *
     * @param p Pattern to use for finding substrings. This will also initialize private field {@see #regex} regex.
     */
    public void test(Pattern p) {
        String tempSample = sample;
        find(p);
        for (String string : resultsList) {
            tempSample = tempSample.replace(string, ANSI_GREEN_FOREGROUND + string + ANSI_RESET);
        }
        result = tempSample;
        System.out.println(this);
    }

    /**
     * Splits {@see #result} result with a discriminant and use the array that
     * {@see String#split(String)} String#split(String) returns as resultList.
     *
     * @param p Pattern to use as discriminant.
     * @return current instance of SRegex
     */
    public SRegex split(Pattern p) {
        String[] args = result.split(p.pattern());
        resultsList = Arrays.asList(args);
        return this;
    }

    /**
     * Splits {@see #result} result with a discriminant and use the array that
     * {@see String#split(String)} String#split(String) returns as resultList.
     *
     * @param p String to use as discriminant.
     * @return current instance of SRegex
     */
    public SRegex split(String p) {
        return split(Pattern.compile(p));
    }

    /**
     * Replace all substrings that matches the {@see #regex} regex pattern.
     *
     * This can only be used if {@see #regex} regex have been initialized by
     * {@see #SRegex(String, Pattern)} SRegex(String, Pattern) constructor,
     * {@see #find(Patter)} find(Pattern), or {@see #test(Pattern)} test(Pattern).
     *
     * @param replacement string to replace regex matches with.
     * @return
     */
    public SRegex replace(String replacement) {
        find(regex);
        resultsList.forEach(s -> result = result.replaceAll(s, replacement));
        return this;
    }

    /**
     * Replace all results that have been found by {@see find(Pattern)} find(Pattern)
     * or {@see test(Pattern)} test(Pattern) and replace resultList.
     *
     * This can only be used if {@see #regex} regex have been initialized by
     * {@see #SRegex(String, Pattern)} SRegex(String, Pattern) constructor,
     * {@see #find(Patter)} find(Pattern), or {@see #test(Pattern)} test(Pattern).
     *
     * @param replacement string to replace regex matches with.
     * @return current instance of SRegex.
     */
    public SRegex replaceAllResults(String replacement) {
        List<String> replaced = new ArrayList<>();
        resultsList.forEach(s -> replaced.add(s.replaceAll(regex.pattern(), replacement)));
        this.resultsList = replaced;
        return this;
    }

    /**
     * Get resultList that have been set by {@see #find(Pattern)} find(Pattern),
     * {@see #test(Pattern)} test(Pattern), {@see #split(Pattern)} split(Pattern),
     * or {@see #replaceAllResult(String)} replaceAllResults(String).
     *
     * @return {@see #resultList} resultList.
     */
    public List<String> getResultsList() {
        return resultsList;
    }

    /**
     * Get result that have been set by {@see #(Pattern)} find(Pattern),
     * {@see #test(Pattern)} test(Pattern), {@see #split(Pattern)} split(Pattern),
     * or {@see #replace(String)}.
     *
     * @return {@see #result} result.
     */
    public String getResult() {
        return result;
    }

    /**
     * Return {@see #regex} regex Pattern that have been initialized by {@see #init(Patter)} init(Pattern),
     * {@see #find(Pattern)} find(Pattern), or {@see #test(Patter)} test(Pattern).
     *
     * @return {@see #regex} regex.
     */
    public Pattern getRegex() {
        return regex;
    }

    /**
     * Get the default sample of SRegex.
     *
     * @return {@see DEFAULT_SAMPLE} DEFAULT_SAMPLE.
     */
    public String getDefaultSample() {
        return DEFAULT_SAMPLE;
    }

    @Override
    public String toString() {
        String out = "Number of results: " + numResults + "\n\n" + "Results: " + getResultsList();
        out += "\n\n" + result;
        return out;
    }

}
