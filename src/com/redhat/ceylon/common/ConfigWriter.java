package com.redhat.ceylon.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

public class ConfigWriter {

    public static void write(CeylonConfig config, File source, File destination) throws IOException {
        boolean overwriteSource = destination.getCanonicalFile().equals(source.getCanonicalFile());
        if (source.isFile()) {
            InputStream in = null;
            OutputStream out = null;
            File tmpFile = null;
            try {
                in = new FileInputStream(source);
                if (overwriteSource) {
                    // Send the output to a temporary file first
                    tmpFile = File.createTempFile(source.getName(), "tmp", source.getParentFile());
                    out = new FileOutputStream(tmpFile);
                } else {
                    out = new FileOutputStream(destination);
                }
                write(config, in, out);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) { }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) { }
                }
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        } else {
            throw new FileNotFoundException("Couldn't open source configuration file");
        }
    }

    public static void write(CeylonConfig config, File source, OutputStream out) throws IOException {
        if (source.isFile()) {
            InputStream in = null;
            try {
                in = new FileInputStream(source);
                write(config, in, out);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) { }
                }
            }
        } else {
            throw new FileNotFoundException("Couldn't open source configuration file");
        }
    }

    public static void write(CeylonConfig config, InputStream in, File destination) throws IOException {
        OutputStream out = null;
        try {
            out = new FileOutputStream(destination);
            write(config, in, out);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) { }
            }
        }
    }

    public static void write(CeylonConfig config, File destination) throws IOException {
        OutputStream out = null;
        try {
            out = new FileOutputStream(destination);
            write(config, out);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) { }
            }
        }
    }

    public static void write(final CeylonConfig config, InputStream in, OutputStream out) throws IOException {
        final Writer writer = new BufferedWriter(new OutputStreamWriter(out, Charset.forName("UTF-8")));
        ConfigReader reader = new ConfigReader(in, new ConfigReaderListener() {
            boolean skipToNewline = false;
            
            @Override
            public void onSection(String section, String text) throws IOException {
                if (config.isSectionDefined(section)) {
                    writer.write(text);
                } else {
                    skipToNewline = true;
                }
            }

            @Override
            public void onOption(String name, String value, String text) throws IOException {
                if (config.isOptionDefined(name)) {
                    writer.write(text);
                } else {
                    skipToNewline = true;
                }
            }

            @Override
            public void onComment(String text) throws IOException {
                if (!skipToNewline) {
                    writer.write(text);
                } else {
                    skipToNewline = !text.contains("\n");
                }
            }

            @Override
            public void onWhitespace(String text) throws IOException {
                if (!skipToNewline) {
                    writer.write(text);
                } else {
                    skipToNewline = !text.contains("\n");
                }
            }
            
        });
        reader.process();
        writer.flush();
        // Now write what's left of the configuration to the output
        write(config, out);
    }

    public static void write(CeylonConfig config, OutputStream out) throws IOException {
        // TODO
    }
}

interface ImprovedConfigReaderListener extends ConfigReaderListener {
    public void onSectionEnd(String section) throws IOException;
}

// This adapter class improves on the standard ConfigReaderListener interface
// by adding an onSectionEnd() event which will be triggered at the end of
// each configuration section. It tries to be smart about this by considering
// whitespace and comments on the last option line to be still part of the
// last section while considering all whitespace and comments before a section
// line to be part of the new section
class ImprovedConfigReaderListenerAdapter implements ConfigReaderListener {
    private ImprovedConfigReaderListener listener;
    
    private String currentSection;
    private boolean skipToNewline;
    private ArrayList<Text> buffer;
    
    interface Text {
        String getText();
    }
    
    class Comment implements Text {
        private String text;
        public Comment(String text) {
            this.text = text;
        }
        @Override
        public String getText() {
            return text;
        }
    }
    
    class Whitespace implements Text {
        private String text;
        public Whitespace(String text) {
            this.text = text;
        }
        @Override
        public String getText() {
            return text;
        }
    }
    
    public ImprovedConfigReaderListenerAdapter(ImprovedConfigReaderListener listener) {
        this.listener = listener;
        this.currentSection = null;
        this.skipToNewline = false;
        this.buffer = new ArrayList<Text>();
    }

    @Override
    public void setup() throws IOException {
        // Ignoring setup
    }

    @Override
    public void onSection(String section, String text) throws IOException {
        if (currentSection != null) {
            listener.onSectionEnd(currentSection);
        }
        flushBuffer();
        currentSection = section;
        listener.onSection(section, text);
        skipToNewline = true;
    }

    @Override
    public void onOption(String name, String value, String text) throws IOException {
        flushBuffer();
        listener.onOption(name, value, text);
        skipToNewline = true;
    }

    @Override
    public void onComment(String text) throws IOException {
        if (skipToNewline) {
            listener.onComment(text);
            skipToNewline = !text.contains("\n");
        } else {
            buffer.add(new Comment(text));
        }
    }

    @Override
    public void onWhitespace(String text) throws IOException {
        if (skipToNewline) {
            listener.onWhitespace(text);
            skipToNewline = !text.contains("\n");
        } else {
            buffer.add(new Whitespace(text));
        }
    }

    @Override
    public void cleanup() throws IOException {
        if (currentSection != null) {
            listener.onSectionEnd(currentSection);
        }
        flushBuffer();
    }
    
    private void flushBuffer() throws IOException {
        for (Text t : buffer) {
            if (t instanceof Comment) {
                listener.onComment(t.getText());
            } else if (t instanceof Whitespace) {
                listener.onWhitespace(t.getText());
            }
        }
        buffer.clear();
    }
}