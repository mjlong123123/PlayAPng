# PlayAPng
Define APngDrawable to play apng file in android app.
1. APngDrawable can decode apng as stream.
2. APngDrawable only cache two frames for playing.
3. Multiple APngDrawable can use the same APngHolder instance to play same apng file. APngHolder hold a APngDecoder and a apng file.
4. When decoder decode apng frame. All frame data use the same byte buffer.And this process decrease memory allocation.

Due to above feature,APngDrawable can play apng file with less memory.Especially App need to play same apng file with multiple ImageView at same times in a page.

# How to use?

        aPngDrawable = new APngDrawable(() -> {
            InputStream inputStream = null;
            try {
                inputStream = getAssets().open(assetsName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return inputStream;
        }, Executors.newFixedThreadPool(2));
        aPngDrawable.setAnimationCallback(new AnimationCallback() {
            @Override
            public void animationStart() {
                Log.d("callback", "animationStart ");
            }

            @Override
            public void animationEnd() {
                Log.d("callback", "animationEnd ");
            }
        });
        aPngDrawable.start();
        imageView.setImageDrawable(aPngDrawable);
