/*
 * Copyright (c) 2020 by k3b.
 *
 * This file is part of #APhotoManager (https://github.com/k3b/APhotoManager/)
 *              and #toGoZip (https://github.com/k3b/ToGoZip/).
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>
 */
package de.k3b.io.filefacade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An {@link IFile} to read from or write to as in memory String.
 * <p>
 * * reads from String
 * * * StringFileFacade in = new StringFileFacade().{@link #setInputString(String)};
 * * * in.{@link #openInputStream()}
 * * * // ...
 * * * in.close()
 * <p>
 * * write to String
 * * * StringFileFacade out = new StringFileFacade();
 * * * out.{@link #openOutputStream()};
 * * * // ...
 * * * out.close()
 * * * String writtenToOut = out.{@link #getOutputString()}
 */
public class StringFileFacade implements IFile {

    private ByteArrayOutputStream byteArrayOutputStream = null;
    private String data = "";

    public StringFileFacade setInputString(String data) {
        this.data = data;
        return this;
    }

    @Override
    public InputStream openInputStream() throws FileNotFoundException {
        return new ByteArrayInputStream(data.getBytes());
    }

    @Override
    public OutputStream openOutputStream() throws FileNotFoundException {
        this.byteArrayOutputStream = new ByteArrayOutputStream();
        return this.byteArrayOutputStream;
    }

    public String getOutputString() {
        if (this.byteArrayOutputStream != null) {
            return new String(byteArrayOutputStream.toByteArray());
        }
        return "";
    }


    @Override
    public boolean renameTo(IFile newName) {
        return false;
    }

    @Override
    public boolean renameTo(String newName) {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public String getAbsolutePath() {
        return "";
    }

    @Override
    public IFile getCanonicalFile() {
        return this;
    }

    @Override
    public String getCanonicalPath() {
        return getAbsolutePath();
    }

    @Override
    public IFile getParentFile() {
        return null;
    }

    @Override
    public String getParent() {
        return "";
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public void setLastModified(long fileTime) {

    }

    @Override
    public long lastModified() {
        return 0;
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public IFile[] listFiles() {
        return new IFile[0];
    }

    @Override
    public boolean copy(IFile targetFullPath, boolean deleteSourceWhenSuccess) throws IOException {
        return false;
    }

    /**
     * @param name
     * @return null if file already exist
     */
    @Override
    public IFile create(String name) {
        return null;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public long length() {
        return 0;
    }
}