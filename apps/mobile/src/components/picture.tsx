import { Image, type ImageSource } from 'expo-image';
import { type StyleProp, type ImageStyle } from 'react-native';

type Props = {
  source: ImageSource;
  size: number;
  // Omit for decorative pictures that sit next to their text label.
  label?: string;
  style?: StyleProp<ImageStyle>;
};

// Square prepared artwork, scaled to fit without cropping.
export function Picture({ source, size, label, style }: Props) {
  return (
    <Image
      accessibilityLabel={label}
      accessible={Boolean(label)}
      contentFit="contain"
      source={source}
      style={[{ width: size, height: size }, style]}
    />
  );
}
