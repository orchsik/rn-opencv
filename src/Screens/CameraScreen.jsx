import React, {Component} from 'react';
import {
  AppRegistry,
  View,
  Text,
  Platform,
  Image,
  TouchableOpacity,
} from 'react-native';
import {RNCamera as Camera} from 'react-native-camera';
import Toast, {DURATION} from 'react-native-easy-toast';

import styles from '../Styles/Screens/CameraScreen';
import OpenCV from '../NativeModules/OpenCV';
import CircleWithinCircle from '../aseets/svg/CircleWithinCircle';

export default class CameraScreen extends Component {
  constructor(props) {
    super(props);

    this.takePicture = this.takePicture.bind(this);
    this.checkForRectangle = this.checkForRectangle.bind(this);
    this.proceedWithcheckForRectangle =
      this.proceedWithcheckForRectangle.bind(this);
    this.repeatPhoto = this.repeatPhoto.bind(this);
    this.usePhoto = this.usePhoto.bind(this);
  }

  state = {
    cameraPermission: false,
    photoAsBase64: {
      content: '',
      origin: '',
      cropped: '',
      isPhotoPreview: false,
      photoPath: '',
    },
  };

  checkForRectangle(imageAsBase64) {
    return new Promise((resolve, reject) => {
      if (Platform.OS === 'android') {
        OpenCV.checkForRectangle(
          imageAsBase64,
          error => {
            console.log('error');
            // error handling
          },
          result => {
            resolve(result);
          },
        );
      } else {
        OpenCV.checkForRectangle(imageAsBase64, (error, dataArray) => {
          resolve(dataArray[0]);
        });
      }
    });
  }

  proceedWithcheckForRectangle() {
    const {content, photoPath} = this.state.photoAsBase64;

    console.log('## proceedWithcheckForRectangle');
    this.checkForRectangle(content)
      .then(([origin, cropped]) => {
        console.log('## new_photo');
        this.setState({
          photoAsBase64: {
            ...this.state.photoAsBase64,
            isPhotoPreview: true,
            photoPath,
            origin,
            cropped,
          },
        });
      })
      .catch(err => {
        console.log('err', err);
      });
  }

  async takePicture() {
    if (this.camera) {
      const options = {quality: 0.5, base64: true};
      const data = await this.camera.takePictureAsync(options);
      this.setState({
        ...this.state,
        photoAsBase64: {
          content: data.base64,
          origin: '',
          cropped: '',
          isPhotoPreview: false,
          photoPath: data.uri,
        },
      });
      this.proceedWithcheckForRectangle();
    }
  }

  repeatPhoto() {
    this.setState({
      ...this.state,
      photoAsBase64: {
        content: '',
        origin: '',
        cropped: '',
        isPhotoPreview: false,
        photoPath: '',
      },
    });
  }

  usePhoto() {
    // do something, e.g. navigate
  }

  render() {
    if (this.state.photoAsBase64.isPhotoPreview) {
      return (
        <View style={styles.container}>
          <Toast ref="toast" position="center" />

          <View style={[styles.imagePreview]}>
            <Image
              source={{
                uri: `data:image/png;base64,${this.state.photoAsBase64.origin}`,
              }}
              style={{height: '100%', width: '100%', resizeMode: 'contain'}}
            />
          </View>
          {this.state.photoAsBase64.cropped && (
            <View style={[styles.imagePreview]}>
              <Image
                source={{
                  uri: `data:image/png;base64,${this.state.photoAsBase64.cropped}`,
                }}
                style={{height: '100%', width: '100%', resizeMode: 'contain'}}
              />
            </View>
          )}

          <View style={styles.repeatPhotoContainer}>
            <TouchableOpacity onPress={this.repeatPhoto}>
              <Text style={styles.photoPreviewRepeatPhotoText}>
                Repeat photo
              </Text>
            </TouchableOpacity>
          </View>
          <View style={styles.usePhotoContainer}>
            <TouchableOpacity onPress={this.usePhoto}>
              <Text style={styles.photoPreviewUsePhotoText}>Use photo</Text>
            </TouchableOpacity>
          </View>
        </View>
      );
    }

    return (
      <View style={styles.container}>
        <Camera
          ref={cam => {
            this.camera = cam;
          }}
          style={styles.preview}
          permissionDialogTitle={'Permission to use camera'}
          permissionDialogMessage={
            'We need your permission to use your camera phone'
          }>
          <View style={styles.takePictureContainer}>
            <TouchableOpacity onPress={this.takePicture}>
              <View>
                <CircleWithinCircle />
              </View>
            </TouchableOpacity>
          </View>
        </Camera>
        <Toast ref="toast" position="center" />
      </View>
    );
  }
}

AppRegistry.registerComponent('CameraScreen', () => CameraScreen);
