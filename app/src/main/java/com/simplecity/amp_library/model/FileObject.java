package com.simplecity.amp_library.model;

import android.content.Context;
import com.simplecity.amp_library.interfaces.FileType;
import com.simplecity.amp_library.utils.FileHelper;
import com.simplecity.amp_library.utils.StringUtils;

public class FileObject extends BaseFileObject {

    public static final String EXTENSION;

    public static final TagInfo TAG_INFO;

    private long duration = 0;

    public FileObject() {
        this.fileType = FileType.FILE;
    }

    public String getTimeString(Context context) {
        if (duration == 0) {
            duration = FileHelper.getDuration(context, this);
        }
        return StringUtils.makeTimeString(context, duration / 1000);
    }

    @Override
    public String toString() {
        return "FileObject{" +
                "extension='" + extension + '\'' +
                ", size='" + size + '\'' +
                "} " + super.toString();
    }
}
