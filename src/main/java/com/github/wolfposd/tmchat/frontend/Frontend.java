/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2016
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.wolfposd.tmchat.frontend;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.github.wolfposd.tmchat.Message;

public class Frontend implements FrontendListener {

    private JFrame frame;
    private JTextField recipient;
    private JTextArea chatlog;
    private JButton sendButton;
    private JTextField chatmessage;
    private String user;
    private ISendMessage sendmessage;

    public Frontend(String user, ISendMessage isendmessage) {

        this.user = user;
        this.sendmessage = isendmessage;
        setupUI();

        frame.setVisible(true);
    }

    public void setLocation(int x, int y) {
        frame.setLocation(x, y);
    }

    private void setupUI() {
        frame = new JFrame(user);

        recipient = new JTextField(12);
        chatlog = new JTextArea();
        chatmessage = new JTextField(15);
        sendButton = new JButton("Send");

        JPanel southpanel = new JPanel();

        southpanel.add(new JLabel("TO:"));
        southpanel.add(recipient);
        southpanel.add(new JLabel("MSG:"));
        southpanel.add(chatmessage);
        southpanel.add(sendButton);

        frame.setLayout(new BorderLayout());
        frame.add(chatlog, BorderLayout.CENTER);
        frame.add(southpanel, BorderLayout.SOUTH);
        frame.setSize(550, 500);
        frame.setLocationRelativeTo(null);

        sendButton.addActionListener(e -> sendButtonPressed());
        chatmessage.addKeyListener(new KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendButtonPressed();
                }
            };
        });

    }

    private void sendButtonPressed() {
        String[] recipients = recipient.getText().split(",");
        String text = chatmessage.getText();
        chatmessage.setText("");
        
        for (String r : recipients) {
            Message m = new Message(user, r, text);
            sendmessage.sendMessage(m);
            chatlog.append(">> " + m.receiver + " : " + m.message + "\n");
        }
        
    }

    @Override
    public void messageIncoming(Message msg) {
        chatlog.append("<< " + msg.sender + " : " + msg.message + "\n");
    }

}
