/*
 * Copyright (c) 2014 Mario Guggenberger <mario.guggenberger@aau.at>
 *
 * This file is part of ITEC MediaPlayer.
 *
 * ITEC MediaPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ITEC MediaPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ITEC MediaPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.protyposis.android.spectaculumdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import net.protyposis.android.spectaculum.ImageView;
import net.protyposis.android.spectaculum.PipelineResolution;

import java.io.IOException;

public class ImageViewActivity extends Activity {

    private static final int REQUEST_LOAD_IMAGE = 1;

    private ImageView mImageView;
    private GLEffects mEffectList;
    private Uri mImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imageview);

        mImageView = (ImageView) findViewById(R.id.imageview);
        mImageView.setPipelineResolution(PipelineResolution.VIEW);
        mImageView.setOnFrameCapturedCallback(new Utils.OnFrameCapturedCallback(this, "spectaculum-image"));

        mEffectList = new GLEffects(this, R.id.parameterlist, mImageView);
        mEffectList.addEffects();

        if(savedInstanceState != null) {
            // Load an already selected image (after configuration change)
            loadImage((Uri)savedInstanceState.getParcelable("uri"));
        } else {
            // Show image selection dialog
            Intent intent = null;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
            } else {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
            }
            startActivityForResult(intent, REQUEST_LOAD_IMAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK) {
            loadImage(data.getData());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void loadImage(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            mImageView.setImageBitmap(bitmap);
            mImageUri = imageUri;
        } catch (IOException e) {
            Log.e("ImageViewActivity", "error loading image", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.common, menu);
        mEffectList.addToMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(mEffectList.doMenuActions(item)) {
            return true;
        } else if(id == R.id.action_save_frame) {
            mImageView.captureFrame();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mImageUri != null) {
            outState.putParcelable("uri", mImageUri);
        }
    }
}
