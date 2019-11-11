@file:Suppress("DEPRECATION")
package com.shenlai.cameraFollow.activity

import android.app.Activity
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.arcsoft.face.*
import com.friendlyarm.FriendlyThings.HardwareControler
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
import kotlin.math.abs
import kotlin.math.max

@ExperimentalStdlibApi
class MainActivity : Activity() {
    private val debug: Boolean by lazy {
        true
    }

    private fun log(log: String) {
        if (debug) Log.i("MainActivity", log)
    }

    private lateinit var frontCameraHelper: CameraHelper
    private lateinit var backCameraHelper: CameraHelper
    private lateinit var frontDrawHelper: DrawHelper
    private lateinit var backDrawHelper: DrawHelper
    private lateinit var frontPreviewSize: Camera.Size
    private lateinit var backPreviewSize: Camera.Size
    private val frontCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT //1
    private val backCameraId = Camera.CameraInfo.CAMERA_FACING_BACK //0
    private var afCode = -1
    private val processMask =
        FaceEngine.ASF_AGE or
                FaceEngine.ASF_FACE3DANGLE or
                FaceEngine.ASF_GENDER or
                FaceEngine.ASF_LIVENESS
    private val initMask =
        FaceEngine.ASF_FACE_DETECT or FaceEngine.ASF_FACE_RECOGNITION or processMask
    private val detectFaceScaleVal = 16 //2-32(default 16)
    private val detectFaceMaxNum = 10 //0-50
    private val faceEngine: FaceEngine by lazy {
        FaceEngine()
    }

    private val mReadBuffSize = 512

    // NanoPC-T4 U_ART4
    private val devName = "/dev/ttyS4"
    private val speed: Long = 9600
    private val dataBits = 8
    private val stopBits = 1
    private var devfd = -1
    private val readBuf = ByteArray(mReadBuffSize)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.attributes.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
        setContentView(R.layout.activity_main)

        if (ConfigUtil.firstUse(baseContext)) {
            activeEngine()
        }
        if (ConfigUtil.getActived(baseContext) == false) {
            activeEngine()
        }
        initHardWare()
        initEngine()
        mCameraFrontPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cameraListener = object : CameraListener {
                    override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
                        log("onCameraOpened: $cameraId  $displayOrientation $isMirror")
                        frontPreviewSize = camera.parameters.previewSize
                        frontDrawHelper = DrawHelper(
                            frontPreviewSize.width,
                            frontPreviewSize.height,
                            mCameraFrontPreview.width,
                            mCameraFrontPreview.height,
                            displayOrientation,
                            cameraId,
                            isMirror,
                            false,
                            false
                        )
                    }


                    override fun onPreview(nv21: ByteArray, camera: Camera) {
                        //log("set mCameraFrontBytes")
                        mCameraFrontBytes = nv21
                    }

                    override fun onCameraClosed() {
                        log("onCameraClosed: ")
                    }

                    override fun onCameraError(e: Exception) {
                        log("onCameraError: " + e.message)
                    }

                    override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                        frontDrawHelper.cameraDisplayOrientation = displayOrientation
                        log("onCameraConfigurationChanged: $cameraID  $displayOrientation")
                    }
                }
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)


                frontCameraHelper = CameraHelper.Builder()
                    .previewViewSize(Point(mCameraFrontPreview.measuredWidth, mCameraFrontPreview.measuredHeight))
                    .rotation(windowManager.defaultDisplay.rotation)
                    .specificCameraId(frontCameraId)
                    .isMirror(true)
                    .previewOn(mCameraFrontPreview)
                    .cameraListener(cameraListener)
                    .previewSize(Point(1280, 720))
                    .build()
                frontCameraHelper.init()
                frontCameraHelper.start()
                mCameraFrontPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        mCameraBackPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val cameraListener = object : CameraListener {
                    override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
//                        if (devfd != -1) {
//                            turningThread.start()
//                        }
                        log("onCameraOpened: $cameraId  $displayOrientation $isMirror")
                        backPreviewSize = camera.parameters.previewSize
                        backDrawHelper = DrawHelper(
                            backPreviewSize.width,
                            backPreviewSize.height,
                            mCameraBackPreview.width,
                            mCameraBackPreview.height,
                            displayOrientation,
                            cameraId,
                            isMirror,
                            false,
                            false
                        )
                    }


                    override fun onPreview(nv21: ByteArray, camera: Camera) {
                        //log("set mCameraBackBytes")
                        mCameraBackBytes = nv21
                    }

                    override fun onCameraClosed() {
                        log("onCameraClosed: ")
                    }

                    override fun onCameraError(e: Exception) {
                        log("onCameraError: " + e.message)
                    }

                    override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                        backDrawHelper.cameraDisplayOrientation = displayOrientation
                        log("onCameraConfigurationChanged: $cameraID  $displayOrientation")
                    }
                }
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)


                backCameraHelper = CameraHelper.Builder()
                    .previewViewSize(Point(mCameraBackPreview.measuredWidth, mCameraBackPreview.measuredHeight))
                    .rotation(windowManager.defaultDisplay.rotation)
                    .specificCameraId(backCameraId)
                    .isMirror(false)
                    .previewOn(mCameraBackPreview)
                    .cameraListener(cameraListener)
                    .previewSize(Point(1280, 720))
                    .build()
                backCameraHelper.init()
                backCameraHelper.start()
                mCameraBackPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private var readyToSend = false
    private var readThread = Thread(Runnable {
        while (true) {
            if (HardwareControler.select(devfd, 0, 0) != 1) {
                continue
            }
            val retSize = HardwareControler.read(devfd, readBuf, mReadBuffSize)
            if (retSize > 0) {
                readyToSend = true
                log("hardware ${sendCnt--} recv:${readBuf.copyOfRange(0, retSize).contentToString()}")
            }
        }
    })

    private fun waitToSend(usec: Long = 10) {
        while (!readyToSend) Thread.sleep(usec)
    }

    private var initHardware: Boolean = false
    private fun resetMotor() {
        synchronized(this) {
            initHardware = false
            if (devfd >= 0) {
                Thread(Runnable {
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(0, true), getByte(1, false), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(0, true), getByte(1, true), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(1, false), getByte(0, true), 0x00.toByte()))
                    waitToSend()
                    send(byteArrayOf(0xA5.toByte(), getByte(1, true), getByte(0, true), 0x00.toByte()))
                    waitToSend()
                    initHardware = true
                    tx = 0
                    ty = 0
                }).start()
            }
        }
    }

    private fun initHardWare() {
        devfd = HardwareControler.openSerialPort(devName, speed, dataBits, stopBits)
        if (devfd >= 0) {
            log("init hardware success")
            readyToSend = true
            readThread.start()
            resetMotor()
        } else {
            devfd = -1
            toast("init hardware failed.")
            log("init hardware failed")
        }
    }

    private var xScale = 40
    private var yScale = 30
    private fun getPosition(rect: Rect, size: Camera.Size): Array<Int> {
        val x = (rect.centerX() - size.width / 2) * xScale / size.width //目标位置x
        val y = (rect.centerY() - size.height / 2) * yScale / size.height //目标位置y
        var cx = x - tx //需要移动的距离x
        var cy = y - ty //需要移动的距离y
        log("face1:当前位置:($tx,$ty) 目标位置($x,$y) 需要移动:($cx,$cy) center:(${rect.centerX()},${rect.centerY()}) size:(${size.width / 2},${size.height / 2})")

        if (cx in -1..1 && cy in -1..1) {
            return arrayOf(0, 0)
        }
        tx = x
        ty = y

        forwardX = cx >= 0
        forwardY = cy <= 0

        if (cx < 0) {
            cx = -cx
        }
        if (cy < 0) {
            cy = -cy
        }
        return arrayOf(cx, cy)
    }

    private var mCameraBackBytes: ByteArray? = null
    private var mCameraFrontBytes: ByteArray? = null


    private fun initEngine() {
        ConfigUtil.setFtOrient(baseContext, FaceEngine.ASF_OP_0_HIGHER_EXT)
        afCode = faceEngine.init(
            this,
            FaceEngine.ASF_DETECT_MODE_VIDEO,
            ConfigUtil.getFtOrient(this),
            detectFaceScaleVal,
            detectFaceMaxNum,
            initMask
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
        frontCameraHelper.release()
        backCameraHelper.release()
        if (devfd != -1) {
            HardwareControler.close(devfd)
            devfd = -1
        }
        unInitEngine()
        super.onDestroy()
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

    private fun getByte(step: Int, forward: Boolean): Byte {
        if (step < 0 || step > 520) {
            throw RuntimeException("step must in[0,60]")
        }
        return (if (forward) step else (step - 128)).toByte()
    }

    private var sendCnt = 0
    private var forwardX = false
    private var forwardY = true
    private var tx = 0
    private var ty: Int = 0

    private fun send(cmd: ByteArray) {
        val ret = HardwareControler.write(devfd, cmd)
        if (ret > 0) {
            readyToSend = false
            ++sendCnt
            val s = "hardware $sendCnt send:${cmd.contentToString()}"
            log(s)
        }
    }

    fun resetParameter(view: View) {
        if (view.id == R.id.mResetParameter) {
            resetMotor()
            xScale = XScale.text.toString().toInt()
            yScale = YScale.text.toString().toInt()
            xxScale = XXScale.text.toString().toInt()
            yyScale = YYScale.text.toString().toInt()
        }
    }

    fun resetMotor(view: View) {
        if (view.id == R.id.mResetMotor) {
            readyToSend = true
            if (tx == 0 && ty == 0) {
                return
            }
            log("Reset:tx:$tx ty:$ty")
            send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(abs(tx), tx < 0),
                    getByte(abs(ty), ty > 0),
                    0x00.toByte()
                )
            )
            tx = 0
            ty = 0
        }
    }

    private var xxScale = 5
    private var yyScale = 5
    //调整人脸到中间
    private fun fineTuning(rect: Rect, size: Camera.Size): Point {
        val x = (rect.centerX() - size.width / 2) * xxScale / size.width //目标x轴偏移位置
        val y = (rect.centerY() - size.height / 2) * yyScale / size.height //目标y轴偏移位置
        var cx = -x //需要移动的距离x
        var cy = -y //需要移动的距离y
        log(
            "face2:当前绝对位置:($tx,$ty) ($x,$y) 目标位置(0,0) 需要移动:($cx,$cy) center:(${rect.centerX()},${rect.centerY()}) size/2:(${size.width / 2},${size
                .height / 2})"
        )

        tx += x
        ty += y

        forwardX = cx <= 0
        forwardY = cy >= 0

        if (cx < 0) {
            cx = -cx
        }
        if (cy < 0) {
            cy = -cy
        }
        return Point(cx, cy)
    }

    //维护已经扫描过的队列
    private var scanList: MutableList<FaceFeature> = ArrayList()

    //是否微调
    private fun addFace(face: FaceFeature): Boolean {
        log("addFace scanList.size:${scanList.size}")
        var maxScore = 0.0f
        val faceSimilar = FaceSimilar()
        for ((idx, face0) in scanList.withIndex()) {
            var code = faceEngine.compareFaceFeature(face0, face, faceSimilar)
            maxScore = max(maxScore, faceSimilar.score)
            log("addFace code:$code idx:$idx score:${faceSimilar.score} maxScore:${faceSimilar.score}")
        }
        if (maxScore < 0.2) {
            if (scanList.size > 50) {
                scanList.removeAt(0)
            }
            scanList.add(face)
            return true
        }
        return false
    }

    //比较是否是同一个人
    private fun compare(face0: FaceFeature, face1: FaceFeature): Boolean {
        var f: FaceSimilar = FaceSimilar()
        faceEngine.compareFaceFeature(face0, face1, f)
        return f.score > 0.5f
    }

    //前置摄像头微调，返回是否微调
    private fun fineTurning(): Boolean {
        val facesList = ArrayList<FaceInfo>()
        var code = faceEngine.detectFaces(
            mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
            facesList
        )

        //是否检测到人脸
        if (code != ErrorInfo.MOK || facesList.isEmpty()) {
            return false
        }

        code = faceEngine.process(
            mCameraFrontBytes,
            frontPreviewSize.width,
            frontPreviewSize.height,
            FaceEngine.CP_PAF_NV21,
            facesList,
            processMask
        )

        //是否有人脸信息
        if (code != ErrorInfo.MOK || facesList.isEmpty()) {
            return false
        }
        if (mBackFaceRectView != null) {
            mBackFaceRectView.clearFaceInfo()
        }
        if (mFrontFaceRectView != null) {
            mFrontFaceRectView.clearFaceInfo()
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
            return false
        }

        if (mFrontFaceRectView != null) {
            val drawInfoList = ArrayList<DrawInfo>()
            for (i in facesList.indices) {
                drawInfoList.add(
                    DrawInfo(
                        frontDrawHelper.adjustRect(facesList[i].rect),
                        genderInfoList[i].gender,
                        ageInfoList[i].age,
                        faceLivenessInfoList[i].liveness, null
                    )
                )
            }
            frontDrawHelper.draw(mFrontFaceRectView, drawInfoList)
        }

        var f = FaceFeature()
        code = faceEngine.extractFaceFeature(
            mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
            facesList[0], f
        )
        if (code == ErrorInfo.MOK) {
            addFace(f)
        }

        var rect = facesList.first().rect
        val xy = fineTuning(rect, frontPreviewSize)
        log("微调$xy")
        if (xy.equals(0, 0)) {
            return false
        }
        send(
            byteArrayOf(
                0xA5.toByte(),
                getByte(xy.x, forwardX),
                getByte(xy.y, forwardY),
                0x00.toByte()
            )
        )
        return true
    }

    private fun turning(): Boolean {
        if (mFrontFaceRectView != null) {
            mFrontFaceRectView.clearFaceInfo()
        }
        val facesList = ArrayList<FaceInfo>()
        var code = faceEngine.detectFaces(
            mCameraBackBytes, backPreviewSize.width, backPreviewSize.height, FaceEngine.CP_PAF_NV21,
            facesList
        )
        if (code != ErrorInfo.MOK || facesList.isEmpty()) {
            log("no face")
            if (mBackFaceRectView != null) {
                mBackFaceRectView.clearFaceInfo()
            }
            return false
        }

        //需要被扫描的id
        var id = 0
        var minScore = 1.0f
        for ((idx, face) in facesList.withIndex()) {
            var f = FaceFeature()
            code = faceEngine.extractFaceFeature(
                mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
                face, f
            )
            if (code != ErrorInfo.MOK) {
                continue
            }

            var similar = FaceSimilar()
            for (face0 in scanList) {
                faceEngine.compareFaceFeature(face0, f, similar)
                if (similar.score > 0.5) {
                    break
                }
            }
            if (similar.score < minScore) {
                id = idx
                minScore = similar.score
            }

            if (minScore < 0.1f) {
                break
            }
        }

        code = faceEngine.process(
            mCameraBackBytes,
            backPreviewSize.width,
            backPreviewSize.height,
            FaceEngine.CP_PAF_NV21,
            facesList,
            processMask
        )
        mCameraBackBytes = null
        if (code != ErrorInfo.MOK || facesList.isEmpty()) {
            return false
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
            return false
        }

        if (mBackFaceRectView != null) {
            val drawInfoList = ArrayList<DrawInfo>()
            for (i in facesList.indices) {
                var name = "n_scan"
                if (i == id) {
                    name = "scanning"
                }
                drawInfoList.add(
                    DrawInfo(
                        backDrawHelper.adjustRect(facesList[i].rect),
                        genderInfoList[i].gender,
                        ageInfoList[i].age,
                        faceLivenessInfoList[i].liveness, name
                    )
                )
            }
            backDrawHelper.draw(mBackFaceRectView, drawInfoList)
            val rect0 = facesList[id].rect
            val xy = getPosition(rect0, backPreviewSize)
            if (xy[0] == 0 && xy[1] == 0) {
                return false
            }
            send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(xy[0], forwardX),
                    getByte(xy[1], forwardY),
                    0x00.toByte()
                )
            )
        }
        return true
    }

    private var turningThread0 = Thread(Runnable {
        while (devfd != -1) {
            log("readyToSend:$readyToSend mCameraFrontBytes==null:${mCameraFrontBytes == null} mCameraBackBytes==null:${mCameraBackBytes == null}")
            if (!readyToSend || (mCameraFrontBytes == null && mCameraBackBytes == null)) {
                Thread.sleep(50)
                continue
            }
            val facesList = ArrayList<FaceInfo>()
            var code = faceEngine.detectFaces(
                mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
                facesList
            )

            var rect: Rect
            if (code == ErrorInfo.MOK && facesList.isNotEmpty()) {
                code = faceEngine.process(
                    mCameraFrontBytes,
                    frontPreviewSize.width,
                    frontPreviewSize.height,
                    FaceEngine.CP_PAF_NV21,
                    facesList,
                    processMask
                )
                mCameraFrontBytes = null
                if (code != ErrorInfo.MOK || facesList.isEmpty()) {
                    continue
                }
                if (mBackFaceRectView != null) {
                    mBackFaceRectView.clearFaceInfo()
                }
                if (mFrontFaceRectView != null) {
                    mFrontFaceRectView.clearFaceInfo()
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
                    continue
                }

                if (mFrontFaceRectView != null) {
                    val drawInfoList = ArrayList<DrawInfo>()
                    for (i in facesList.indices) {
                        drawInfoList.add(
                            DrawInfo(
                                frontDrawHelper.adjustRect(facesList[i].rect),
                                genderInfoList[i].gender,
                                ageInfoList[i].age,
                                faceLivenessInfoList[i].liveness, null
                            )
                        )
                    }
                    frontDrawHelper.draw(mFrontFaceRectView, drawInfoList)
                }

                var f = FaceFeature()
                faceEngine.extractFaceFeature(
                    mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
                    facesList[0], f
                )
                addFace(f)

                //TODO 微调
                rect = facesList.first().rect
                val xy = fineTuning(rect, frontPreviewSize)
                log("微调$xy")
                if (xy.equals(0, 0)) {
                    continue
                }
                send(
                    byteArrayOf(
                        0xA5.toByte(),
                        getByte(xy.x, forwardX),
                        getByte(xy.y, forwardY),
                        0x00.toByte()
                    )
                )
            } else {
                if (mFrontFaceRectView != null) {
                    mFrontFaceRectView.clearFaceInfo()
                }
                //TODO 寻找人脸
                code = faceEngine.detectFaces(
                    mCameraBackBytes, backPreviewSize.width, backPreviewSize.height, FaceEngine.CP_PAF_NV21,
                    facesList
                )
                if (code == ErrorInfo.MOK && facesList.isNotEmpty()) {

                    var fst: MutableList<FaceFeature> = ArrayList()
                    for (face in facesList) {
                        var f = FaceFeature()
                        faceEngine.extractFaceFeature(
                            mCameraFrontBytes, frontPreviewSize.width, frontPreviewSize.height, FaceEngine.CP_PAF_NV21,
                            face, f
                        )
                        fst.add(f)
                    }

                    //TODO 调整
                    code = faceEngine.process(
                        mCameraBackBytes,
                        backPreviewSize.width,
                        backPreviewSize.height,
                        FaceEngine.CP_PAF_NV21,
                        facesList,
                        processMask
                    )
                    mCameraBackBytes = null
                    if (code != ErrorInfo.MOK || facesList.isEmpty()) {
                        continue
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
                        continue
                    }

                    if (mBackFaceRectView != null) {
                        val drawInfoList = ArrayList<DrawInfo>()
                        for (i in facesList.indices) {
                            drawInfoList.add(
                                DrawInfo(
                                    backDrawHelper.adjustRect(facesList[i].rect),
                                    genderInfoList[i].gender,
                                    ageInfoList[i].age,
                                    faceLivenessInfoList[i].liveness, null
                                )
                            )
                        }
                        backDrawHelper.draw(mBackFaceRectView, drawInfoList)
                        val rect0 = facesList[0].rect
                        val xy = getPosition(rect0, backPreviewSize)
                        if (xy[0] == 0 && xy[1] == 0) {
                            continue
                        }
                        send(
                            byteArrayOf(
                                0xA5.toByte(),
                                getByte(xy[0], forwardX),
                                getByte(xy[1], forwardY),
                                0x00.toByte()
                            )
                        )
                    }
                } else {
                    //TODO 没检测到人
                    log("no face")
                    if (mBackFaceRectView != null) {
                        mBackFaceRectView.clearFaceInfo()
                    }
                }
            }
        }
    })
    private var turningThread = Thread(Runnable {
        while (devfd != -1) {
            //log("readyToSend:$readyToSend mCameraFrontBytes==null:${mCameraFrontBytes == null} mCameraBackBytes==null:${mCameraBackBytes == null}")
            if (!readyToSend || (mCameraFrontBytes == null && mCameraBackBytes == null)) {
                Thread.sleep(50)
                continue
            }
            log("  ")
            log("  ")
            log("\n\n-------------------------------------------------------------------------------------")
            if (fineTurning()) {
                log("fineTurning")
                continue
            }
            mCameraFrontBytes = null
            if (turning()) {
                log("turning")
                continue
            }
            log("no turning...")
        }
    })

    fun start(view: View) {
        if (view.id == R.id.mStart) {
            if (devfd != -1) {
                if (!turningThread.isAlive) {
                    turningThread.start()
                }
            }
        }
    }

    fun click(view: View) {
        when (view.id) {
            R.id.mUp -> send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(0, true),
                    getByte(1, true),
                    0x00.toByte()
                )
            )
            R.id.mDown -> send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(0, true),
                    getByte(1, false),
                    0x00.toByte()
                )
            )
            R.id.mLeft -> send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(1, true),
                    getByte(0, true),
                    0x00.toByte()
                )
            )
            R.id.mRight -> send(
                byteArrayOf(
                    0xA5.toByte(),
                    getByte(1, false),
                    getByte(0, true),
                    0x00.toByte()
                )
            )
        }
    }

    private fun startRecord() {
        frontCameraHelper.record()
        backCameraHelper.record()
    }

    private fun stopRecord() {
        frontCameraHelper.stopRecord()
        backCameraHelper.stopRecord()
    }

    fun record(view: View) {
        when (view.id) {
            R.id.mStartRecord -> {
                startRecord()
                mStartRecord.isClickable = false
                mStopRecord.isClickable = true
            }
            R.id.mStopRecord -> {
                stopRecord()
                mStartRecord.isClickable = true
                mStopRecord.isClickable = false
            }
        }
    }
}
