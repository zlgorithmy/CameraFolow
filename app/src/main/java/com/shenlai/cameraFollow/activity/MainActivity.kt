package com.shenlai.cameraFollow.activity

import android.app.Activity
import android.graphics.Point
import android.hardware.Camera
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.arcsoft.face.*
import com.shenlai.cameraFollow.R
import com.shenlai.cameraFollow.common.Constants
import com.shenlai.cameraFollow.model.DrawInfo
import com.shenlai.cameraFollow.util.ConfigUtil
import com.shenlai.cameraFollow.util.DrawHelper
import com.shenlai.cameraFollow.util.camera.CameraHelper
import com.shenlai.cameraFollow.util.camera.CameraListener
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.util.*

class MainActivity : Activity() {
    private val debug: Boolean by lazy {
        true
    }

    private fun log(log: String) {
        if (debug) Log.i("MainActivity", log)
    }

    private lateinit var cameraHelper: CameraHelper
    private lateinit var drawHelper: DrawHelper
    private lateinit var previewSize: Camera.Size
    private val rgbCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT
    private var afCode = -1
    private val processMask = FaceEngine.ASF_AGE or FaceEngine.ASF_FACE3DANGLE or FaceEngine.ASF_GENDER or FaceEngine.ASF_LIVENESS
    private val faceEngine: FaceEngine by lazy {
        FaceEngine()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.attributes.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
        setContentView(R.layout.activity_main)

        if (ConfigUtil.getActived(baseContext) == false) {
            activeEngine()
        }
        initEngine()
        mCamera0Preview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                initCamera()
                mCamera0Preview.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun initEngine() {
        ConfigUtil.setFtOrient(baseContext, FaceEngine.ASF_OP_0_HIGHER_EXT)
        afCode = faceEngine.init(
            this,
            FaceEngine.ASF_DETECT_MODE_VIDEO,
            ConfigUtil.getFtOrient(this),
            16,
            20,
            FaceEngine.ASF_FACE_DETECT or
                    FaceEngine.ASF_AGE or
                    FaceEngine.ASF_FACE3DANGLE or
                    FaceEngine.ASF_GENDER or
                    FaceEngine.ASF_LIVENESS
        )
        val versionInfo = VersionInfo()
        faceEngine.getVersion(versionInfo)
        log("initEngine:  init: $afCode  version:$versionInfo")
        if (afCode != ErrorInfo.MOK) {
            toast(getString(R.string.init_failed, afCode))
        }
    }

    private fun unInitEngine() {

        if (afCode == 0) {
            afCode = faceEngine.unInit()
            log("unInitEngine: $afCode")
        }
    }


    override fun onDestroy() {
        cameraHelper.release()
        unInitEngine()
        super.onDestroy()
    }

    private fun initCamera() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val cameraListener = object : CameraListener {
            override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
                log("onCameraOpened: $cameraId  $displayOrientation $isMirror")
                previewSize = camera.parameters.previewSize
                drawHelper = DrawHelper(
                    previewSize.width,
                    previewSize.height,
                    mCamera0Preview.width,
                    mCamera0Preview.height,
                    displayOrientation,
                    cameraId,
                    isMirror,
                    false,
                    false
                )
            }


            override fun onPreview(nv21: ByteArray, camera: Camera) {

                if (mFaceRectView != null) {
                    mFaceRectView.clearFaceInfo()
                }
                val faceInfoList = ArrayList<FaceInfo>()
                var code = faceEngine.detectFaces(nv21, previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList)
                if (code == ErrorInfo.MOK && faceInfoList.size > 0) {
                    //log("onPreview: " + faceInfoList.size + " (" + previewSize!!.width + "," + previewSize!!.height + ")")
                    code = faceEngine.process(nv21, previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, faceInfoList, processMask)
                    if (code != ErrorInfo.MOK) {
                        return
                    }
                } else {
                    return
                }

                val ageInfoList = ArrayList<AgeInfo>()
                val genderInfoList = ArrayList<GenderInfo>()
                val face3DAngleList = ArrayList<Face3DAngle>()
                val faceLivenessInfoList = ArrayList<LivenessInfo>()
                val ageCode = faceEngine.getAge(ageInfoList)
                val genderCode = faceEngine.getGender(genderInfoList)
                val face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList)
                val livenessCode = faceEngine.getLiveness(faceLivenessInfoList)

                //有其中一个的错误码不为0，return
                if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
                    return
                }
                if (mFaceRectView != null) {
                    val drawInfoList = ArrayList<DrawInfo>()
                    for (i in faceInfoList.indices) {
                        drawInfoList.add(
                            DrawInfo(
                                drawHelper.adjustRect(faceInfoList[i].rect),
                                genderInfoList[i].gender,
                                ageInfoList[i].age,
                                faceLivenessInfoList[i].liveness, null
                            )
                        )
                    }
                    drawHelper.draw(mFaceRectView, drawInfoList)
                }
            }

            override fun onCameraClosed() {
                log("onCameraClosed: ")
            }

            override fun onCameraError(e: Exception) {
                log("onCameraError: " + e.message)
            }

            override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                drawHelper.cameraDisplayOrientation = displayOrientation
                log("onCameraConfigurationChanged: $cameraID  $displayOrientation")
            }
        }
        cameraHelper = CameraHelper.Builder()
            .previewViewSize(Point(mCamera0Preview.measuredWidth, mCamera0Preview.measuredHeight))
            .rotation(windowManager.defaultDisplay.rotation)
            .specificCameraId(rgbCameraId)
            //.specificCameraId(0)
            .isMirror(true)
            .previewOn(mCamera0Preview)
            .cameraListener(cameraListener)
            .build()
        cameraHelper.init()
        cameraHelper.start()
    }


    private fun activeEngine() {
        Observable.create(ObservableOnSubscribe<Int> { emitter ->
            val activeCode = faceEngine.activeOnline(
                baseContext,
                Constants.APP_ID,
                Constants.SDK_KEY
            )
            emitter.onNext(activeCode)
        })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Int> {
                override fun onSubscribe(d: Disposable) {

                }

                override fun onNext(activeCode: Int) {
                    when (activeCode) {
                        ErrorInfo.MOK -> toast(getString(R.string.active_success))
                        ErrorInfo.MERR_ASF_ALREADY_ACTIVATED -> toast(getString(R.string.already_activated))
                        else -> toast(getString(R.string.active_failed, activeCode))
                    }

                    val activeFileInfo = ActiveFileInfo()
                    val res = faceEngine.getActiveFileInfo(baseContext, activeFileInfo)
                    if (res == ErrorInfo.MOK) {
                        log(activeFileInfo.toString())
                        ConfigUtil.setActived(baseContext, true)
                        ConfigUtil.setFtOrient(baseContext, FaceEngine.ASF_OP_270_ONLY)
                    }
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }

                override fun onComplete() {

                }
            })
    }
}
